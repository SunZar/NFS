package ru.archzar.nearfieldstorage.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;

public class VaultPartsTest {
    private static final char[] PASSWORD = "секретная фраза".toCharArray();
    private static final String TEXT = "Почта: secret!\nКод от замка: 3456🔐";

    @Test
    public void assemblesEncodedPartsInOrder() throws Exception {
        List<VaultParts.Part> parts = VaultParts.create(TEXT, PASSWORD, 3);
        VaultParts.Assembly assembly = new VaultParts.Assembly();

        for (int i = 0; i < parts.size(); i++) {
            VaultParts.Part part = VaultParts.Part.parse(parts.get(i).encode());
            assertEquals(i + 1, part.number);
            assertEquals(3, part.count);
            assembly.add(part);
        }
        assertEquals(TEXT, assembly.decrypt(PASSWORD));
    }

    @Test
    public void rejectsIncompleteOutOfOrderAndMixedSets() throws Exception {
        List<VaultParts.Part> parts = VaultParts.create(TEXT, PASSWORD, 3);
        List<VaultParts.Part> other = VaultParts.create(TEXT, PASSWORD, 3);
        VaultParts.Assembly assembly = new VaultParts.Assembly();

        assertThrows(IllegalArgumentException.class, () -> assembly.add(parts.get(1)));
        assembly.add(parts.get(0));
        assertThrows(IllegalArgumentException.class, () -> assembly.add(parts.get(0)));
        assertThrows(IllegalArgumentException.class, () -> assembly.add(other.get(1)));
        assertThrows(IllegalStateException.class, () -> assembly.decrypt(PASSWORD));
        assembly.add(parts.get(1));
        assembly.add(parts.get(2));
        assertEquals(TEXT, assembly.decrypt(PASSWORD));
    }

    @Test
    public void setIdentifierIsAuthenticated() throws Exception {
        byte[] encoded = VaultParts.create(TEXT, PASSWORD, 1).get(0).encode();
        encoded[1] ^= 1;
        VaultParts.Assembly assembly = new VaultParts.Assembly();
        assembly.add(VaultParts.Part.parse(encoded));

        assertThrows(GeneralSecurityException.class, () -> assembly.decrypt(PASSWORD));
    }

    @Test
    public void exportsAndReopensCompleteMultipartVaultWithoutTags() throws Exception {
        VaultParts.Assembly original = new VaultParts.Assembly();
        for (VaultParts.Part part : VaultParts.create(TEXT, PASSWORD, 3)) {
            original.add(part);
        }

        String dump = original.toDump();
        assertFalse(dump.contains(TEXT));
        assertFalse(dump.contains(new String(PASSWORD)));
        VaultParts.Assembly imported = readDump("\ufeff" + dump.replace("\n", "\r\n"));

        assertEquals(3, imported.count());
        assertEquals(TEXT, imported.decrypt(PASSWORD));
        assertEquals(dump, imported.toDump());
        assertThrows(GeneralSecurityException.class,
                () -> imported.decrypt("другой пароль".toCharArray()));
    }

    @Test
    public void refusesIncompleteVaultsAndInvalidDumpFiles() {
        assertThrows(IllegalStateException.class, () -> new VaultParts.Assembly().toDump());
        assertThrows(IllegalArgumentException.class, () -> readDump("обычный текст"));
        assertThrows(IllegalArgumentException.class, () -> readDump("NFS-DUMP-1\n!!!"));
        assertThrows(IllegalArgumentException.class, () -> readDump("NFS-DUMP-1\nAAAA"));
        assertThrows(IllegalArgumentException.class, () -> readDump("A".repeat(400_000)));
    }

    private static VaultParts.Assembly readDump(String text) throws IOException {
        return VaultParts.readDump(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
}
