/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.credential;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The key generated for a start without a configured one: written once, reused afterwards, kept
 * from other users, and never in the way of a key the operator configured.
 */
class EncryptionKeyFileTest {

    @TempDir
    Path dataDirectory;

    @Test
    void generatesAKeyOnceAndReadsTheSameKeyBackOnEveryLaterStart() {
        Path path = dataDirectory.resolve("secrets").resolve("encryption.key");

        String first = new EncryptionKeyFile(path).keyFor("");
        String second = new EncryptionKeyFile(path).keyFor(null);

        assertTrue(Files.exists(path));
        assertEquals(first, second);
        assertEquals(32, Base64.getDecoder().decode(first).length);
    }

    @Test
    void theGeneratedKeyIsReadableByTheOwnerOnly() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path path = dataDirectory.resolve("secrets").resolve("encryption.key");

        new EncryptionKeyFile(path).loadOrCreate();

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path)));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path.getParent())));
    }

    @Test
    void aConfiguredKeyWinsAndNoFileIsWritten() {
        Path path = dataDirectory.resolve("secrets").resolve("encryption.key");
        String configured = Base64.getEncoder().encodeToString(new byte[32]);

        assertEquals(configured, new EncryptionKeyFile(path).keyFor(configured));
        assertFalse(Files.exists(path));
    }

    @Test
    void anExistingFileIsNeverReplaced() throws IOException {
        Path path = dataDirectory.resolve("encryption.key");
        String existing = Base64.getEncoder().encodeToString(new byte[32]);
        Files.writeString(path, existing + "\n");

        assertEquals(existing, new EncryptionKeyFile(path).loadOrCreate());
        assertEquals(existing + "\n", Files.readString(path));
    }

    @Test
    void aCipherBuiltOnTheGeneratedKeyOpensWhatAnotherBuiltOnTheSameFileSealed() {
        Path path = dataDirectory.resolve("secrets").resolve("encryption.key");
        var sealing = new CredentialCipher(new EncryptionKeyFile(path).keyFor(""));
        var opening = new CredentialCipher(new EncryptionKeyFile(path).keyFor(""));

        assertEquals("secret", opening.unseal(sealing.seal("secret")));
    }
}
