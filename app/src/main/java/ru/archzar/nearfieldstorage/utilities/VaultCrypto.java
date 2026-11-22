package ru.archzar.nearfieldstorage.utilities;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class VaultCrypto {
    private static final byte VERSION = 1;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int HEADER_BYTES = 1 + SALT_BYTES + NONCE_BYTES;
    static final int OVERHEAD_BYTES = HEADER_BYTES + TAG_BYTES;
    private static final int ITERATIONS = 600_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private VaultCrypto() {}

    static boolean isSupported(byte[] data) {
        return data.length >= OVERHEAD_BYTES && data[0] == VERSION;
    }

    static byte[] encrypt(String text, char[] password, byte[] context)
            throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(nonce);

        byte[] header = new byte[HEADER_BYTES];
        header[0] = VERSION;
        System.arraycopy(salt, 0, header, 1, SALT_BYTES);
        System.arraycopy(nonce, 0, header, 1 + SALT_BYTES, NONCE_BYTES);

        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, nonce, header, context);
        byte[] plaintext = text.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] result = Arrays.copyOf(header, header.length + encrypted.length);
            System.arraycopy(encrypted, 0, result, header.length, encrypted.length);
            return result;
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    static String decrypt(byte[] data, char[] password, byte[] context)
            throws GeneralSecurityException {
        if (!isSupported(data)) {
            throw new GeneralSecurityException("Unsupported vault format");
        }
        byte[] header = Arrays.copyOf(data, HEADER_BYTES);
        byte[] salt = Arrays.copyOfRange(header, 1, 1 + SALT_BYTES);
        byte[] nonce = Arrays.copyOfRange(header, 1 + SALT_BYTES, HEADER_BYTES);
        Cipher cipher = cipher(Cipher.DECRYPT_MODE, password, salt, nonce, header, context);
        byte[] plaintext = cipher.doFinal(data, HEADER_BYTES, data.length - HEADER_BYTES);
        try {
            return new String(plaintext, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private static Cipher cipher(int mode, char[] password, byte[] salt, byte[] nonce,
                                 byte[] header, byte[] context) throws GeneralSecurityException {
        if (password.length == 0) {
            throw new GeneralSecurityException("Empty master password");
        }
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
                    .getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BYTES * Byte.SIZE, nonce));
            cipher.updateAAD(header);
            cipher.updateAAD(context);
            return cipher;
        } finally {
            spec.clearPassword();
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
        }
    }
}
