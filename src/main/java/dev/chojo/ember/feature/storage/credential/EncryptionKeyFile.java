/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.credential;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * The key Ember encrypts its secrets at rest with when the operator has not configured one.
 *
 * <p>The key lives in a file under the data directory and never in the database, so a dump of the
 * database alone holds nothing it could decrypt. It is written once, on the first start that needs
 * it, readable by the owner only where the file system supports that, and read back unchanged on
 * every later start. Losing the file loses every secret encrypted with it, which is why the data
 * directory belongs in every backup.
 *
 * <p>The file is created with {@code CREATE_NEW}, so two starts racing for it cannot both write
 * one: the loser reads what the winner wrote.
 */
public final class EncryptionKeyFile {
    /** Where the generated key is kept, relative to the working directory like the rest of {@code data/}. */
    public static final Path DEFAULT_PATH = Path.of("data", "secrets", "encryption.key");

    private static final Logger log = LoggerFactory.getLogger(EncryptionKeyFile.class);
    private static final int KEY_BYTES = 32;
    private static final Set<PosixFilePermission> OWNER_ONLY_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY = PosixFilePermissions.fromString("rwx------");

    private final Path path;

    /**
     * @param path where the key is read from, and written to when it does not exist yet
     */
    public EncryptionKeyFile(Path path) {
        this.path = path;
    }

    /**
     * The key to use: the configured one when there is one, the one in the file otherwise.
     *
     * @param configured the base64 key from the configuration, blank when none is set
     * @return the base64 key
     */
    public String keyFor(String configured) {
        if (configured != null && !configured.isBlank()) return configured;
        return loadOrCreate();
    }

    /**
     * Reads the key from the file, generating and writing it first when the file does not exist.
     *
     * @return the base64 key
     */
    public String loadOrCreate() {
        try {
            if (Files.exists(path)) return read();
            return create();
        } catch (FileAlreadyExistsException e) {
            return readUnchecked();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read or create the encryption key at " + path, e);
        }
    }

    private String create() throws IOException {
        Path directory = path.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        restrict(directory, OWNER_ONLY_DIRECTORY);
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        Files.writeString(path, encoded, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        restrict(path, OWNER_ONLY_FILE);
        log.warn(
                "No encryption key was configured, generated one at {}. Keep it in every backup of the data directory.",
                path.toAbsolutePath());
        return encoded;
    }

    private String read() throws IOException {
        return Files.readString(path, StandardCharsets.US_ASCII).strip();
    }

    private String readUnchecked() {
        try {
            return read();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the encryption key at " + path, e);
        }
    }

    /**
     * Narrows the permissions to the owner. A file system without POSIX permissions keeps what it
     * has, since the data directory is expected to belong to the service account anyway.
     */
    private static void restrict(Path target, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(target, permissions);
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("Could not restrict the permissions of {}: {}", target, e.getMessage());
        }
    }
}
