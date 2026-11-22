package ru.archzar.nearfieldstorage.utilities;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;

public class VaultCryptoTest {
    private static final char[] PASSWORD = "длинный пароль 🔐".toCharArray();
    private static final byte[] CONTEXT = {1, 2, 3};
    private static final String CONTENTS = "Почта\nuser@example.com\nSesame! 🔑\n";

    @Test
    public void roundTripsUnicodeAndPreservesWhitespace() throws Exception {
        byte[] encrypted = VaultCrypto.encrypt(CONTENTS, PASSWORD, CONTEXT);

        assertTrue(VaultCrypto.isSupported(encrypted));
        assertEquals(CONTENTS, VaultCrypto.decrypt(encrypted, PASSWORD, CONTEXT));
        assertArrayEquals("длинный пароль 🔐".toCharArray(), PASSWORD);
    }

    @Test
    public void eachWriteUsesFreshRandomParameters() throws Exception {
        byte[] first = VaultCrypto.encrypt(CONTENTS, PASSWORD, CONTEXT);
        byte[] second = VaultCrypto.encrypt(CONTENTS, PASSWORD, CONTEXT);

        assertFalse(Arrays.equals(first, second));
        assertEquals(CONTENTS, VaultCrypto.decrypt(second, PASSWORD, CONTEXT));
    }

    @Test
    public void rejectsWrongPasswordAndModifiedCiphertext() throws Exception {
        byte[] encrypted = VaultCrypto.encrypt(CONTENTS, PASSWORD, CONTEXT);
        assertThrows(AEADBadTagException.class,
                () -> VaultCrypto.decrypt(encrypted, "не тот пароль".toCharArray(), CONTEXT));

        encrypted[encrypted.length - 1] ^= 1;
        assertThrows(AEADBadTagException.class, () -> VaultCrypto.decrypt(encrypted, PASSWORD, CONTEXT));
    }

    @Test
    public void rejectsTruncatedAndUnknownFormats() throws Exception {
        byte[] encrypted = VaultCrypto.encrypt("", PASSWORD, CONTEXT);
        byte[] truncated = Arrays.copyOf(encrypted, encrypted.length - 1);
        encrypted[0] = 127;

        assertFalse(VaultCrypto.isSupported(truncated));
        assertFalse(VaultCrypto.isSupported(encrypted));
        assertThrows(GeneralSecurityException.class, () -> VaultCrypto.decrypt(encrypted, PASSWORD, CONTEXT));
    }
}
