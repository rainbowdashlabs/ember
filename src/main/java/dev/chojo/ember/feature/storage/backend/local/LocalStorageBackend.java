/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.local;

import dev.chojo.ember.feature.storage.backend.BackendCapability;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.tree.FileTreeBackend;
import dev.chojo.ember.feature.storage.backend.tree.SharedTree;
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
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Backend that stores bytes under a configurable directory root (default {@code data/}).
 *
 * <p>The partial write, the rename, the sidecar and the walk are {@link FileTreeBackend}'s; this adds
 * what only a local disk can do. It is the only backend that satisfies
 * {@link BackendCapability#ACCESS_TIME_TRACKING} (the modification time is what is read and updated,
 * since the access time of a filesystem is unreliable) and {@link BackendCapability#POSIX_MODE}.
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
                new SharedTree<>(tree),
                "",
                EnumSet.of(BackendCapability.ACCESS_TIME_TRACKING, BackendCapability.POSIX_MODE));
        this.tree = tree;
        this.root = tree.resolve("");
    }

    private static Set<PosixFilePermission> parsePosixMode(int octalMode) {
        return PosixFilePermissions.fromString(toRwxString(octalMode));
    }

    private static String toRwxString(int octalMode) {
        StringBuilder sb = new StringBuilder(9);
        int[] groups = {(octalMode >> 6) & 7, (octalMode >> 3) & 7, octalMode & 7};
        for (int g : groups) {
            sb.append((g & 4) != 0 ? 'r' : '-');
            sb.append((g & 2) != 0 ? 'w' : '-');
            sb.append((g & 1) != 0 ? 'x' : '-');
        }
        return sb.toString();
    }

    /**
     * Applies a POSIX mode (octal string, e.g. {@code "0600"}) to {@code fullKey}. Skips when
     * the filesystem does not support POSIX permissions (Windows). The method is invoked by
     * {@code StorageService} after every successful write of a {@code POSIX_MODE} category.
     */
    public void applyPosixMode(String fullKey, String posixMode) {
        Path target = resolve(fullKey);
        try {
            Files.setPosixFilePermissions(target, parsePosixMode(Integer.parseInt(posixMode, 8)));
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

    /**
     * Absolute root directory the backend is rooted at. Visible for diagnostics and tests.
     */
    public Path root() {
        return root;
    }

    /**
     * Resolves a {@code fullKey} to its on-disk absolute path under {@link #root()}.
     */
    public Path resolve(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) {
            throw new IllegalArgumentException("fullKey must not be empty");
        }
        return tree.resolve(fullKey);
    }

    /**
     * Recursively removes everything in {@code prefix} (object + metadata + empty dirs), in one walk
     * rather than one delete per key.
     */
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
