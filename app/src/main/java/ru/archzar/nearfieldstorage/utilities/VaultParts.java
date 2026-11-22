package ru.archzar.nearfieldstorage.utilities;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class VaultParts {
    public static final int MAX_PARTS = 32;
    public static final int MAX_BYTES = 256 * 1024;

    private static final byte VERSION = 1;
    private static final int CONTEXT_BYTES = 1 + 2 * Long.BYTES + 1;
    public static final int PART_HEADER_BYTES = CONTEXT_BYTES + 1;
    private static final String DUMP_HEADER = "NFS-DUMP-1";
    private static final int MAX_DUMP_BYTES = 4 * ((CONTEXT_BYTES + MAX_BYTES + 2) / 3) + 8192;

    private VaultParts() {}

    public static int utf8Bytes(CharSequence text) {
        int bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character < 0x80) {
                bytes++;
            } else if (character < 0x800) {
                bytes += 2;
            } else if (Character.isSurrogate(character)) {
                if (Character.isHighSurrogate(character) && i + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(i + 1))) {
                    bytes += 4;
                    i++;
                } else {
                    // String.getBytes(UTF_8) encodes an unpaired surrogate as '?'.
                    bytes++;
                }
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    public static int encryptedSize(int textBytes) {
        return textBytes + VaultCrypto.OVERHEAD_BYTES;
    }

    public static int textCapacity(int encryptedCapacity) {
        return Math.max(0, Math.min(MAX_BYTES, encryptedCapacity) - VaultCrypto.OVERHEAD_BYTES);
    }

    public static int chunkSize(int encryptedBytes, int count, int slot) {
        return (slot + 1) * encryptedBytes / count - slot * encryptedBytes / count;
    }

    public static List<Part> create(String text, char[] password, int count)
            throws GeneralSecurityException {
        if (count < 1 || count > MAX_PARTS || text.length() > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid size");
        }
        UUID id = UUID.randomUUID();
        byte[] encrypted = VaultCrypto.encrypt(text, password, context(id, count).array());
        if (encrypted.length > MAX_BYTES) {
            throw new IllegalArgumentException("Vault too large");
        }
        List<Part> parts = new ArrayList<>(count);
        int start = 0;
        for (int index = 0; index < count; index++) {
            int end = start + chunkSize(encrypted.length, count, index);
            parts.add(new Part(id, index + 1, count, Arrays.copyOfRange(encrypted, start, end)));
            start = end;
        }
        return Collections.unmodifiableList(parts);
    }

    private static ByteBuffer context(UUID id, int count) {
        return ByteBuffer.allocate(CONTEXT_BYTES).put(VERSION)
                .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
                .put((byte) count);
    }

    public static Assembly readDump(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int size;
        while ((size = input.read(buffer)) != -1) {
            if (bytes.size() + size > MAX_DUMP_BYTES) {
                throw new IllegalArgumentException("Dump too large");
            }
            bytes.write(buffer, 0, size);
        }
        return fromDump(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    private static Assembly fromDump(String dump) {
        int line = dump.indexOf('\n');
        int start = dump.startsWith("\ufeff") ? 1 : 0;
        if (line <= start || !DUMP_HEADER.equals(dump.substring(start, line).trim())) {
            throw new IllegalArgumentException("Unknown dump format");
        }
        String encoded = dump.substring(line + 1).replaceAll("[ \t\r\n]", "");
        byte[] data = Base64.getDecoder().decode(encoded);
        if (data.length <= CONTEXT_BYTES || data.length > CONTEXT_BYTES + MAX_BYTES) {
            throw new IllegalArgumentException("Invalid dump length");
        }
        ByteBuffer bytes = ByteBuffer.wrap(data);
        if (bytes.get() != VERSION) {
            throw new IllegalArgumentException("Unknown vault version");
        }
        UUID id = new UUID(bytes.getLong(), bytes.getLong());
        int count = Byte.toUnsignedInt(bytes.get());
        byte[] encrypted = Arrays.copyOfRange(data, CONTEXT_BYTES, data.length);
        if (count < 1 || count > MAX_PARTS || !VaultCrypto.isSupported(encrypted)) {
            throw new IllegalArgumentException("Invalid vault metadata");
        }
        Assembly assembly = new Assembly();
        assembly.id = id;
        assembly.count = count;
        assembly.received = count;
        assembly.encrypted.write(encrypted, 0, encrypted.length);
        return assembly;
    }

    public static final class Part {
        private final UUID id;
        public final int number;
        public final int count;
        private final byte[] chunk;

        private Part(UUID id, int number, int count, byte[] chunk) {
            this.id = id;
            this.number = number;
            this.count = count;
            this.chunk = chunk;
        }

        public byte[] encode() {
            return ByteBuffer.allocate(PART_HEADER_BYTES + chunk.length)
                    .put(context(id, count).array()).put((byte) number).put(chunk).array();
        }

        public static Part parse(byte[] encoded) {
            if (encoded.length <= PART_HEADER_BYTES || encoded.length > PART_HEADER_BYTES + MAX_BYTES) {
                throw new IllegalArgumentException("Invalid part size");
            }
            ByteBuffer bytes = ByteBuffer.wrap(encoded);
            if (bytes.get() != VERSION) {
                throw new IllegalArgumentException("Unsupported part version");
            }
            UUID id = new UUID(bytes.getLong(), bytes.getLong());
            int count = Byte.toUnsignedInt(bytes.get());
            int number = Byte.toUnsignedInt(bytes.get());
            if (count < 1 || count > MAX_PARTS || number < 1 || number > count) {
                throw new IllegalArgumentException("Invalid part index");
            }
            byte[] chunk = new byte[bytes.remaining()];
            bytes.get(chunk);
            return new Part(id, number, count, chunk);
        }
    }

    public static final class Assembly {
        private final ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        private UUID id;
        private int count;
        private int received;

        public int nextNumber() {
            return received + 1;
        }

        public int count() {
            return count;
        }

        public boolean isComplete() {
            return count > 0 && received == count;
        }

        public void add(Part part) {
            if (part.number != nextNumber()
                    || (id != null && (!id.equals(part.id) || count != part.count))
                    || encrypted.size() + part.chunk.length > MAX_BYTES) {
                throw new IllegalArgumentException("Part does not match this assembly");
            }
            id = part.id;
            count = part.count;
            received++;
            encrypted.write(part.chunk, 0, part.chunk.length);
        }

        public byte[] encryptedBytes() {
            if (!isComplete()) {
                throw new IllegalStateException("Incomplete vault");
            }
            return encrypted.toByteArray();
        }

        public String decrypt(char[] password) throws GeneralSecurityException {
            return VaultCrypto.decrypt(encryptedBytes(), password, context(id, count).array());
        }

        public String toDump() {
            byte[] encrypted = encryptedBytes();
            byte[] data = ByteBuffer.allocate(CONTEXT_BYTES + encrypted.length)
                    .put(context(id, count).array()).put(encrypted).array();
            return DUMP_HEADER + "\n" + Base64.getEncoder().encodeToString(data) + "\n";
        }
    }
}
