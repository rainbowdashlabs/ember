/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.credential;

import dev.chojo.ember.util.RandomTokens;
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
import java.util.Objects;
import java.util.Set;

/**
 * The key Ember encrypts its secrets at rest with when the operator has not configured one.
 *
 * <p>The key lives in a file under the data directory, never in the database, so a database dump alone
 * decrypts nothing; losing the file loses every secret encrypted with it. It is written once, owner-only
 * where the file system allows, with {@code CREATE_NEW} so of two racing starts the loser reads what the
 * winner wrote.
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
     * The configured base64 key when there is one, the one in the file otherwise.
     *
     * @param configured the key from the configuration, blank when none is set
     */
    public String keyFor(String configured) {
        if (configured != null && !configured.isBlank()) return configured;
        return loadOrCreate();
    }

    /** The base64 key in the file, generated and written first when there is none. */
    public String loadOrCreate() {
        try {
            try {
                return Files.exists(path) ? read() : create();
            } catch (FileAlreadyExistsException raced) {
                return read();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read or create the encryption key at " + path, e);
        }
    }

    private String create() throws IOException {
        Path directory = Objects.requireNonNull(
                path.toAbsolutePath().getParent(), "the key file is a file, so its absolute path has a parent");
        Files.createDirectories(directory);
        restrict(directory, OWNER_ONLY_DIRECTORY);
        String encoded = RandomTokens.base64(KEY_BYTES);
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
