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
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import ru.archzar.nearfieldstorage.R;
import ru.archzar.nearfieldstorage.utilities.VaultParts;

public final class NfcVault {
    private static final String MIME_TYPE = "application/vnd.archzar.nfs";
    private static final int MAX_SHORT_PAYLOAD = 255;
    private static final int SHORT_NDEF_OVERHEAD = mimeMessage(new byte[0]).getByteArrayLength();
    private static final int LONG_NDEF_OVERHEAD =
            mimeMessage(new byte[MAX_SHORT_PAYLOAD + 1]).getByteArrayLength()
                    - MAX_SHORT_PAYLOAD - 1;

    private NfcVault() {}

    private static NdefMessage mimeMessage(byte[] payload) {
        return new NdefMessage(NdefRecord.createMime(MIME_TYPE, payload));
    }

    static int maxChunkBytes(int capacity) {
        int longPayload = capacity - LONG_NDEF_OVERHEAD;
        int payload = longPayload > MAX_SHORT_PAYLOAD
                ? longPayload : Math.min(MAX_SHORT_PAYLOAD, capacity - SHORT_NDEF_OVERHEAD);
        return Math.max(0, Math.min(VaultParts.MAX_BYTES, payload - VaultParts.PART_HEADER_BYTES));
    }

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

    public static final class ClearSession {
        private final byte[] targetId;
        private volatile boolean writing;
        private volatile boolean completed;

        private ClearSession(byte[] targetId) {
            this.targetId = targetId;
        }

        public static ClearSession identify(Tag tag) throws Problem {
            if (!requireNdef(tag).isWritable()) {
                throw new Problem(R.string.tag_read_only);
            }
            return new ClearSession(requireId(tag));
        }

        public boolean completed() {
            return completed;
        }

        public boolean isWriting() {
            return writing;
        }

        public void clear(Tag tag, BooleanSupplier active)
                throws IOException, FormatException, Problem {
            if (completed || !active.getAsBoolean()) {
                return;
            }
            byte[] id = requireId(tag);
            if (!Arrays.equals(targetId, id)) {
                throw new Problem(R.string.clear_same_tag);
            }
            writing = true;
            try (Ndef ndef = requireNdef(tag)) {
                ndef.connect();
                if (!ndef.isWritable()) {
                    throw new Problem(R.string.tag_read_only);
                }
                if (!active.getAsBoolean()) {
                    return;
                }
                NdefRecord empty = new NdefRecord(NdefRecord.TNF_EMPTY, null, null, null);
                NdefMessage reset = new NdefMessage(empty);
                ndef.writeNdefMessage(reset);
                if (!reset.equals(ndef.getNdefMessage())) {
                    throw new Problem(R.string.verify_failed);
                }
                completed = true;
            } finally {
                writing = false;
            }
        }
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

        public int chunkCapacity() {
            return maxChunkBytes(capacity);
        }

        public int freeChunkBytes() {
            return Math.max(0, chunkCapacity() - (payload.length - VaultParts.PART_HEADER_BYTES));
        }

        public boolean isWritable() {
            return writable;
        }
    }

    public static final class WriteSession {
        private static final int CHECKPOINT_VERSION = 2;
        private static final int MAX_ID_BYTES = 64;
        private static final int MAX_RECORD_BYTES = VaultParts.MAX_BYTES + 128;

        private final AtomicFile checkpoint;
        private final List<Snapshot> original;
        private final List<NdefMessage> messages;
        private final int encryptedBytes;
        private final Problem writeProblem;
        private final List<byte[]> writtenIds = new ArrayList<>();
        private volatile List<byte[]> checkedBlankIds = Collections.emptyList();
        private volatile Problem capacityProblem;
        private byte[] attemptedId;
        private volatile int completed;
        private volatile boolean started;
        private volatile boolean writing;

        public WriteSession(File file, List<VaultParts.Part> parts, List<Snapshot> original) {
            this(new AtomicFile(file), makeMessages(parts), new ArrayList<>(original));
        }

        private WriteSession(AtomicFile checkpoint, List<NdefMessage> messages,
                             List<Snapshot> original) {
            if (messages.size() < original.size()) {
                throw new IllegalArgumentException("Missing replacement parts");
            }
            this.checkpoint = checkpoint;
            this.messages = messages;
            this.original = original;
            encryptedBytes = messages.stream().mapToInt(message ->
                    message.getRecords()[0].getPayload().length - VaultParts.PART_HEADER_BYTES).sum();
            writeProblem = validateOriginals();
        }

        private static List<NdefMessage> makeMessages(List<VaultParts.Part> parts) {
            List<NdefMessage> messages = new ArrayList<>(parts.size());
            for (VaultParts.Part part : parts) {
                messages.add(mimeMessage(part.encode()));
            }
            return messages;
        }

        private Problem validateOriginals() {
            for (int slot = 0; slot < original.size(); slot++) {
                if (!original.get(slot).writable) {
                    return new Problem(R.string.original_read_only, slot + 1);
                }
            }
            for (int slot = 0; slot < original.size(); slot++) {
                try {
                    checkCapacity(slot, original.get(slot).capacity);
                } catch (Problem error) {
                    return error;
                }
            }
            return null;
        }

        public Problem writeProblem() {
            return writeProblem;
        }

        public Problem capacityProblem() {
            return capacityProblem;
        }

        public static WriteSession restore(File file) throws IOException {
            AtomicFile checkpoint = new AtomicFile(file);
            try (DataInputStream in = new DataInputStream(checkpoint.openRead())) {
                int version = in.readInt();
                if (version != 1 && version != CHECKPOINT_VERSION) {
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
                if (version >= 2) {
                    int checked = in.readUnsignedByte();
                    if (checked != 0 && checked != count - originalCount) {
                        throw new IOException("Invalid capacity check count");
                    }
                    List<byte[]> checkedIds = new ArrayList<>(checked);
                    for (int i = 0; i < checked; i++) {
                        checkedIds.add(readBytes(in, MAX_ID_BYTES));
                    }
                    session.checkedBlankIds = checkedIds;
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
            out.writeByte(checkedBlankIds.size());
            for (byte[] id : checkedBlankIds) {
                writeBytes(out, id);
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

        public String toDump() throws Problem {
            VaultParts.Assembly prepared = new VaultParts.Assembly();
            for (NdefMessage message : messages) {
                prepared.add(VaultParts.Part.parse(payload(message)));
            }
            return prepared.toDump();
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
            int checked = checkedBlankIds.size();
            return (needsCapacityCheck(checked) ? original.size() + checked : slot(completed)) + 1;
        }

        public boolean nextIsOriginal() {
            return slot(completed) < original.size();
        }

        public boolean isCheckingCapacity() {
            return needsCapacityCheck(checkedBlankIds.size());
        }

        public boolean hasCheckedBlankTags() {
            return !checkedBlankIds.isEmpty();
        }

        private boolean needsCapacityCheck(int checked) {
            int blanks = count() - original.size();
            return !started && blanks > 1 && checked < blanks;
        }

        public synchronized void writeNext(Tag tag, BooleanSupplier active)
                throws IOException, FormatException, Problem {
            if (completed >= count() || !active.getAsBoolean()) {
                return;
            }
            if (writeProblem != null) {
                throw writeProblem;
            }
            writing = true;
            try {
                if (isCheckingCapacity()) {
                    checkBlankTag(tag, active);
                } else {
                    writePart(tag, slot(completed), active);
                }
                capacityProblem = null;
            } catch (Problem error) {
                capacityProblem = error.isCapacityIssue() ? error : null;
                if (capacityProblem != null && !started && !isCheckingCapacity()) {
                    checkedBlankIds = Collections.emptyList();
                }
                throw error;
            } finally {
                writing = false;
            }
        }

        private void checkBlankTag(Tag tag, BooleanSupplier active)
                throws IOException, FormatException, Problem {
            int slot = original.size() + checkedBlankIds.size();
            byte[] id = requireId(tag);
            if (original.stream().anyMatch(existing -> Arrays.equals(existing.id, id))
                    || checkedBlankIds.stream().anyMatch(existing -> Arrays.equals(existing, id))) {
                throw new Problem(R.string.check_different_tag, slot + 1);
            }
            try (Ndef ndef = requireNdef(tag)) {
                ndef.connect();
                if (!isEmpty(ndef.getNdefMessage())) {
                    throw new Problem(R.string.tag_changed);
                }
                if (!ndef.isWritable()) {
                    throw new Problem(R.string.tag_read_only);
                }
                checkCapacity(slot, ndef.getMaxSize());
                if (active.getAsBoolean()) {
                    List<byte[]> checked = new ArrayList<>(checkedBlankIds);
                    checked.add(id);
                    checkedBlankIds = checked;
                }
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
                    || (old == null && !checkedBlankIds.isEmpty()
                            && !Arrays.equals(checkedBlankIds.get(slot - original.size()), id))
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
                checkCapacity(slot, ndef.getMaxSize());
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

        private void checkCapacity(int slot, int available) throws Problem {
            if (messages.get(slot).getByteArrayLength() <= available) {
                return;
            }
            int chunkCapacity = maxChunkBytes(available);
            int count = minimumTags(slot, chunkCapacity);
            int symbols = VaultParts.textCapacity(chunkCapacity);
            if (count > VaultParts.MAX_PARTS) {
                throw new Problem(R.string.tag_capacity_limit, slot + 1, symbols, symbols / 2,
                        VaultParts.MAX_PARTS);
            }
            throw new Problem(R.string.tag_capacity_more, slot + 1, symbols, symbols / 2, count);
        }

        private int minimumTags(int measuredSlot, int measuredCapacity) {
            for (int count = Math.max(1, original.size()); count <= VaultParts.MAX_PARTS; count++) {
                boolean fits = true;
                for (int slot = 0; slot < count; slot++) {
                    int capacity = slot < original.size() && slot != measuredSlot
                            ? original.get(slot).chunkCapacity() : measuredCapacity;
                    if (VaultParts.chunkSize(encryptedBytes, count, slot) > capacity) {
                        fits = false;
                        break;
                    }
                }
                if (fits) {
                    return count;
                }
            }
            return VaultParts.MAX_PARTS + 1;
        }
    }

    public static final class Problem extends Exception {
        public final int message;
        public final Object[] arguments;

        private Problem(int message, Object... arguments) {
            this.message = message;
            this.arguments = arguments;
        }

        public boolean isCapacityIssue() {
            return message == R.string.tag_capacity_more || message == R.string.tag_capacity_limit;
        }
    }
}
