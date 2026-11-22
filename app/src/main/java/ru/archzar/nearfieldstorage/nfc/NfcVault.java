package ru.archzar.nearfieldstorage.nfc;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import ru.archzar.nearfieldstorage.R;
import ru.archzar.nearfieldstorage.utilities.VaultParts;

public final class NfcVault {
    private static final String MIME_TYPE = "application/vnd.archzar.nfs";

    private NfcVault() {}

    public static Snapshot read(Tag tag) throws IOException, FormatException, Problem {
        try (Ndef ndef = requireNdef(tag)) {
            ndef.connect();
            NdefMessage message = ndef.getNdefMessage();
            if (isEmpty(message)) {
                throw new Problem(R.string.empty_tag);
            }
            return new Snapshot(requireId(tag), payload(message), ndef.getMaxSize(),
                    ndef.isWritable());
        }
    }

    private static byte[] requireId(Tag tag) throws Problem {
        byte[] id = tag.getId();
        if (id == null || id.length == 0 || id.length > 64) {
            throw new Problem(R.string.tag_without_id);
        }
        return id;
    }

    private static Ndef requireNdef(Tag tag) throws Problem {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) {
            throw new Problem(R.string.tag_not_ndef);
        }
        return ndef;
    }

    private static boolean isEmpty(NdefMessage message) {
        if (message == null) {
            return true;
        }
        NdefRecord[] records = message.getRecords();
        return records.length == 1 && records[0].getTnf() == NdefRecord.TNF_EMPTY;
    }

    private static byte[] payload(NdefMessage message) throws Problem {
        if (message != null) {
            NdefRecord[] records = message.getRecords();
            if (records.length == 1 && records[0].getId().length == 0
                    && MIME_TYPE.equals(records[0].toMimeType())) {
                return records[0].getPayload();
            }
        }
        throw new Problem(R.string.foreign_tag);
    }

    public static final class Snapshot {
        private final byte[] id;
        private final byte[] payload;
        private final int capacity;
        private final boolean writable;

        private Snapshot(byte[] id, byte[] payload, int capacity, boolean writable) {
            this.id = id;
            this.payload = payload;
            this.capacity = capacity;
            this.writable = writable;
        }

        public VaultParts.Part part() {
            return VaultParts.Part.parse(payload);
        }

        public boolean sameTag(Snapshot other) {
            return Arrays.equals(id, other.id);
        }
    }

    public static final class WriteSession {
        private static final int CHECKPOINT_VERSION = 1;
        private static final int MAX_ID_BYTES = 64;
        private static final int MAX_RECORD_BYTES = VaultParts.MAX_BYTES + 128;

        private final AtomicFile checkpoint;
        private final List<Snapshot> original;
        private final List<NdefMessage> messages;
        private final List<byte[]> writtenIds = new ArrayList<>();
        private byte[] attemptedId;
        private volatile int completed;
        private volatile boolean started;
        private volatile boolean writing;

        public WriteSession(File file, List<VaultParts.Part> parts, List<Snapshot> original)
                throws Problem {
            checkpoint = new AtomicFile(file);
            this.original = new ArrayList<>(original);
            if (parts.size() < original.size()) {
                throw new IllegalArgumentException("Missing replacement parts");
            }
            messages = new ArrayList<>(parts.size());
            for (VaultParts.Part part : parts) {
                NdefMessage message = new NdefMessage(
                        NdefRecord.createMime(MIME_TYPE, part.encode()));
                messages.add(message);
                int slot = part.number - 1;
                if (slot < original.size()) {
                    Snapshot old = original.get(slot);
                    if (!old.writable) {
                        throw new Problem(R.string.original_read_only, part.number);
                    }
                    checkCapacity(part.number, message, old.capacity);
                }
            }
        }

        private WriteSession(AtomicFile checkpoint, List<NdefMessage> messages,
                             List<Snapshot> original) {
            this.checkpoint = checkpoint;
            this.messages = messages;
            this.original = original;
        }

        public static WriteSession restore(File file) throws IOException {
            AtomicFile checkpoint = new AtomicFile(file);
            try (DataInputStream in = new DataInputStream(checkpoint.openRead())) {
                if (in.readInt() != CHECKPOINT_VERSION) {
                    throw new IOException("Unsupported write checkpoint");
                }
                int count = in.readUnsignedByte();
                int originalCount = in.readUnsignedByte();
                int completed = in.readUnsignedByte();
                if (count < 1 || count > VaultParts.MAX_PARTS
                        || originalCount > count || completed > count) {
                    throw new IOException("Invalid write checkpoint counts");
                }
                List<NdefMessage> messages = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    messages.add(new NdefMessage(readBytes(in, MAX_RECORD_BYTES)));
                }
                List<Snapshot> original = new ArrayList<>(originalCount);
                for (int i = 0; i < originalCount; i++) {
                    original.add(new Snapshot(readBytes(in, MAX_ID_BYTES),
                            readBytes(in, MAX_RECORD_BYTES), in.readInt(), in.readBoolean()));
                }
                WriteSession session = new WriteSession(checkpoint, messages, original);
                for (int i = 0; i < completed; i++) {
                    session.writtenIds.add(readBytes(in, MAX_ID_BYTES));
                }
                if (in.readBoolean()) {
                    session.attemptedId = readBytes(in, MAX_ID_BYTES);
                }
                session.completed = completed;
                session.started = true;
                return session;
            } catch (FileNotFoundException absent) {
                if (file.exists()) {
                    throw absent;
                }
                return null;
            } catch (FormatException malformed) {
                throw new IOException("Invalid write checkpoint record", malformed);
            }
        }

        private static byte[] readBytes(DataInputStream in, int maxLength) throws IOException {
            int length = in.readInt();
            if (length < 1 || length > maxLength) {
                throw new IOException("Invalid write checkpoint field");
            }
            byte[] bytes = new byte[length];
            in.readFully(bytes);
            return bytes;
        }

        private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
            out.writeInt(bytes.length);
            out.write(bytes);
        }

        private void save(int confirmed, byte[] attempt) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(CHECKPOINT_VERSION);
            out.writeByte(count());
            out.writeByte(original.size());
            out.writeByte(confirmed);
            for (NdefMessage message : messages) {
                writeBytes(out, message.toByteArray());
            }
            for (Snapshot old : original) {
                writeBytes(out, old.id);
                writeBytes(out, old.payload);
                out.writeInt(old.capacity);
                out.writeBoolean(old.writable);
            }
            for (byte[] id : writtenIds) {
                writeBytes(out, id);
            }
            out.writeBoolean(attempt != null);
            if (attempt != null) {
                writeBytes(out, attempt);
            }
            byte[] entry = bytes.toByteArray();

            FileOutputStream stream = checkpoint.startWrite();
            boolean saved = false;
            try {
                stream.write(entry);
                stream.getFD().sync();
                checkpoint.finishWrite(stream);
                saved = true;
            } finally {
                if (!saved) {
                    checkpoint.failWrite(stream);
                }
            }
            if (!Arrays.equals(entry, checkpoint.readFully())) {
                throw new IOException("Write checkpoint was not committed");
            }
        }

        public void discard() {
            checkpoint.delete();
        }

        public int count() {
            return messages.size();
        }

        public int completed() {
            return completed;
        }

        public boolean hasStarted() {
            return started;
        }

        public boolean isWriting() {
            return writing;
        }

        private int slot(int written) {
            int extra = messages.size() - original.size();
            // With an expanded set, write the new blanks before touching the originals.
            return written < extra ? original.size() + written : written - extra;
        }

        public int nextNumber() {
            return slot(completed) + 1;
        }

        public boolean nextIsOriginal() {
            return slot(completed) < original.size();
        }

        public synchronized void writeNext(Tag tag, BooleanSupplier active)
                throws IOException, FormatException, Problem {
            if (completed >= count() || !active.getAsBoolean()) {
                return;
            }
            writing = true;
            try {
                writePart(tag, slot(completed), active);
            } finally {
                writing = false;
            }
        }

        private void writePart(Tag tag, int slot, BooleanSupplier active)
                throws IOException, FormatException, Problem {
            byte[] id = requireId(tag);
            Snapshot old = slot < original.size() ? original.get(slot) : null;
            boolean retry = Arrays.equals(attemptedId, id);
            if ((old != null && !Arrays.equals(old.id, id))
                    || (attemptedId != null && !retry)
                    || writtenIds.stream().anyMatch(previous -> Arrays.equals(previous, id))
                    || (old == null && original.stream()
                            .anyMatch(previous -> Arrays.equals(previous.id, id)))) {
                throw new Problem(R.string.wrong_write_tag, slot + 1);
            }

            NdefMessage replacement = messages.get(slot);
            try (Ndef ndef = requireNdef(tag)) {
                ndef.connect();
                NdefMessage current;
                try {
                    current = ndef.getNdefMessage();
                } catch (FormatException damaged) {
                    if (!retry) {
                        throw damaged;
                    }
                    current = null;
                }
                if (retry && replacement.equals(current)) {
                    advance(id);
                    return;
                }
                if (!isEmpty(current)) {
                    if (old == null || !Arrays.equals(old.payload, payload(current))) {
                        throw new Problem(R.string.tag_changed);
                    }
                } else if (old != null && !retry) {
                    throw new Problem(R.string.tag_changed);
                }
                if (!ndef.isWritable()) {
                    throw new Problem(R.string.tag_read_only);
                }
                checkCapacity(slot + 1, replacement, ndef.getMaxSize());
                if (!active.getAsBoolean()) {
                    return;
                }
                if (!retry) {
                    save(completed, id);
                    attemptedId = id;
                    started = true;
                }
                if (!active.getAsBoolean()) {
                    return;
                }
                ndef.writeNdefMessage(replacement);
                if (!replacement.equals(ndef.getNdefMessage())) {
                    throw new Problem(R.string.verify_failed);
                }
                advance(id);
            }
        }

        private void advance(byte[] id) throws IOException {
            writtenIds.add(id);
            try {
                save(completed + 1, null);
            } catch (IOException error) {
                writtenIds.remove(writtenIds.size() - 1);
                throw error;
            }
            attemptedId = null;
            completed++;
        }
    }

    private static void checkCapacity(int number, NdefMessage message, int available)
            throws Problem {
        int required = message.getByteArrayLength();
        if (required > available) {
            throw new Problem(R.string.tag_too_small, number, required, available);
        }
    }

    public static final class Problem extends Exception {
        public final int message;
        public final Object[] arguments;

        private Problem(int message, Object... arguments) {
            this.message = message;
            this.arguments = arguments;
        }
    }
}
