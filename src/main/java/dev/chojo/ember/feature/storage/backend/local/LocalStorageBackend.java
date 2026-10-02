/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.local;

import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.tree.FileTreeBackend;
import dev.chojo.ember.feature.storage.backend.tree.TreeSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Stores bytes under a directory root, {@code data/} by default. The only backend that tracks access
 * times, through the modification time since the access time of a filesystem is unreliable, and the
 * only one that applies POSIX modes.
 */
@Singleton
public class LocalStorageBackend extends FileTreeBackend {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageBackend.class);

    private final Path root;
    private final LocalFileTree tree;

    @Inject
    public LocalStorageBackend() {
        this(Paths.get("data"));
    }

    public LocalStorageBackend(Path root) {
        this(new LocalFileTree(root.toAbsolutePath().normalize()));
    }

    private LocalStorageBackend(LocalFileTree tree) {
        super(
                StorageBackendType.LOCAL,
                TreeSource.shared(tree),
                tree.resolve("").toString(),
                "");
        this.tree = tree;
        this.root = tree.resolve("");
    }

    private static Set<PosixFilePermission> permissions(String octalMode) {
        int mode = Integer.parseInt(octalMode, 8);
        var permissions = EnumSet.noneOf(PosixFilePermission.class);
        for (PosixFilePermission permission : PosixFilePermission.values()) {
            if ((mode & (0400 >> permission.ordinal())) != 0) permissions.add(permission);
        }
        return permissions;
    }

    /**
     * Applies an octal POSIX mode such as {@code "0600"} to a key; skipped where the filesystem keeps
     * no POSIX modes.
     */
    public void applyPosixMode(String fullKey, String posixMode) {
        Path target = resolve(fullKey);
        try {
            Files.setPosixFilePermissions(target, permissions(posixMode));
        } catch (UnsupportedOperationException ignored) {
            log.debug("The filesystem of {} keeps no POSIX modes", fullKey);
        } catch (IOException e) {
            log.warn("Failed to apply POSIX mode {} to {}", posixMode, fullKey, e);
        }
    }

    @Override
    public void touch(String fullKey) {
        Path target = resolve(fullKey);
        try {
            Files.setLastModifiedTime(target, FileTime.from(Instant.now()));
        } catch (NoSuchFileException ignored) {
            log.debug("{} is gone and keeps no access time", fullKey);
        } catch (IOException e) {
            throw new StorageException("Local touch failed for " + fullKey, e);
        }
    }

    @Override
    public Optional<Instant> lastAccessed(String fullKey) {
        Path target = resolve(fullKey);
        if (!Files.isRegularFile(target)) return Optional.empty();
        try {
            return Optional.of(Files.getLastModifiedTime(target).toInstant());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** The absolute directory the backend is rooted at. */
    public Path root() {
        return root;
    }

    /** The path on disk of a key. */
    public Path resolve(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) {
            throw new IllegalArgumentException("fullKey must not be empty");
        }
        return tree.resolve(fullKey);
    }

    /** Removes everything under a prefix, objects, sidecars and directories, in one walk. */
    public void deletePrefix(String prefix) {
        Path base = resolve(prefix);
        if (!Files.exists(base)) return;
        if (Files.isRegularFile(base)) {
            delete(prefix);
            return;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("Could not delete {} under {}", path, prefix, e);
                }
            });
        } catch (IOException e) {
            log.warn("Local deletePrefix failed for {}", prefix, e);
        }
    }
}
