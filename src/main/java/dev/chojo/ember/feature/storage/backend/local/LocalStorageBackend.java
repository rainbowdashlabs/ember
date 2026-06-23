/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chojo.ember.feature.storage.backend.BackendCapability;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.MetadataSidecar;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Backend that stores bytes under a configurable directory root (default {@code data/}).
 *
 * <p>Writes are atomic: bytes flow into a sibling {@code <fullKey>.partial.<uuid>} file and
 * are renamed onto {@code <fullKey>} on success. Metadata is persisted next to the bytes as
 * {@code <fullKey>.meta.json}. A POSIX mode supplied via {@link ObjectMetadata#contentType()
 * — sorry —} the per-category {@code posixMode} flag is applied via
 * {@link Files#setPosixFilePermissions} when the backend declares
 * {@link BackendCapability#POSIX_MODE}.
 *
 * <p>Local is the only backend implementation that can satisfy {@link
 * BackendCapability#ACCESS_TIME_TRACKING} in V1 (via {@code Files.setLastModifiedTime} —
 * filesystem atime is unreliable, mtime is what we read and update).
 */
@Singleton
public class LocalStorageBackend implements StorageBackend {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageBackend.class);
    private static final String META_SUFFIX = ".meta.json";
    private static final String PROBE_PREFIX = "_probe";
    private static final int TRANSFER_BUFFER_BYTES = 8 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path root;

    @Inject
    public LocalStorageBackend() {
        this(Paths.get("data"));
    }

    public LocalStorageBackend(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public StorageBackendType type() {
        return StorageBackendType.LOCAL;
    }

    @Override
    public Set<BackendCapability> capabilities() {
        return EnumSet.of(BackendCapability.ACCESS_TIME_TRACKING, BackendCapability.POSIX_MODE);
    }

    @Override
    public void store(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata) {
        Path target = resolve(fullKey);
        Path partial = target.resolveSibling(target.getFileName() + ".partial." + UUID.randomUUID());
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(partial)) {
                byte[] buffer = new byte[TRANSFER_BUFFER_BYTES];
                int read;
                while ((read = body.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            writeMetadataSidecar(target, metadata);
        } catch (IOException e) {
            safeDelete(partial);
            throw new StorageException("Local store failed for " + fullKey, e);
        }
    }

    @Override
    public void updateMetadata(String fullKey, ObjectMetadata metadata) {
        Path target = resolve(fullKey);
        if (!Files.isRegularFile(target)) {
            throw new StorageException("Cannot update metadata; missing object " + fullKey);
        }
        try {
            writeMetadataSidecar(target, metadata);
        } catch (IOException e) {
            throw new StorageException("Local updateMetadata failed for " + fullKey, e);
        }
    }

    /**
     * Applies a POSIX mode (octal string, e.g. {@code "0600"}) to {@code fullKey}. Skips when
     * the filesystem does not support POSIX permissions (Windows). The method is invoked by
     * {@code StorageService} after every successful write of a {@code POSIX_MODE} category.
     */
    public void applyPosixMode(String fullKey, String posixMode) {
        Path target = resolve(fullKey);
        try {
            int mode = Integer.parseInt(posixMode, 8);
            Set<PosixFilePermission> perms = parsePosixMode(mode);
            Files.setPosixFilePermissions(target, perms);
        } catch (UnsupportedOperationException ignored) {
        } catch (IOException e) {
            log.warn("Failed to apply POSIX mode {} to {}", posixMode, fullKey, e);
        }
    }

    @Override
    public Optional<StoredStream> read(String fullKey) {
        Path target = resolve(fullKey);
        if (!Files.isRegularFile(target)) return Optional.empty();
        try {
            long size = Files.size(target);
            ObjectMetadata metadata = readMetadataSidecar(target);
            InputStream stream = Files.newInputStream(target);
            return Optional.of(new StoredStream(stream, size, metadata));
        } catch (IOException e) {
            throw new StorageException("Local read failed for " + fullKey, e);
        }
    }

    @Override
    public void delete(String fullKey) {
        Path target = resolve(fullKey);
        safeDelete(target);
        safeDelete(metadataPath(target));
    }

    @Override
    public boolean exists(String fullKey) {
        return Files.isRegularFile(resolve(fullKey));
    }

    @Override
    public List<String> listByPrefix(String prefix) {
        Path base = resolve(prefix);
        if (!Files.exists(base)) return List.of();
        var out = new ArrayList<String>();
        if (Files.isRegularFile(base)) {
            out.add(prefix);
            return out;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .map(this::relativize)
                    .filter(key -> !key.endsWith(META_SUFFIX))
                    .filter(key -> !key.contains(".partial."))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            throw new StorageException("Local list failed for prefix " + prefix, e);
        }
        return out;
    }

    @Override
    public long sumSizeByPrefix(String prefix) {
        Path base = resolve(prefix);
        if (!Files.exists(base)) return 0;
        long total = 0;
        try (Stream<Path> walk = Files.walk(base)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                if (name.endsWith(META_SUFFIX) || name.contains(".partial.")) continue;
                total += Files.size(p);
            }
        } catch (IOException e) {
            throw new StorageException("Local size sum failed for prefix " + prefix, e);
        }
        return total;
    }

    @Override
    public HealthStatus probe() {
        String key = PROBE_PREFIX + "/" + UUID.randomUUID();
        Path target = resolve(key);
        try {
            Files.createDirectories(root);
            Files.createDirectories(target.getParent());
            Files.writeString(target, "probe");
            String readBack = Files.readString(target);
            if (!"probe".equals(readBack)) {
                return HealthStatus.unhealthy("Local backend read returned unexpected payload");
            }
        } catch (IOException e) {
            return HealthStatus.unhealthy("Local backend probe failed: " + e.getMessage());
        } finally {
            safeDelete(target);
        }
        return HealthStatus.ok();
    }

    @Override
    public void touch(String fullKey) {
        Path target = resolve(fullKey);
        try {
            Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.from(Instant.now()));
        } catch (NoSuchFileException ignored) {
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

    /** Absolute root directory the backend is rooted at. Visible for diagnostics and tests. */
    public Path root() {
        return root;
    }

    /** Resolves a {@code fullKey} to its on-disk absolute path under {@link #root()}. */
    public Path resolve(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) {
            throw new IllegalArgumentException("fullKey must not be empty");
        }
        Path resolved = root.resolve(fullKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Key escapes storage root: " + fullKey);
        }
        return resolved;
    }

    private Path metadataPath(Path target) {
        return target.resolveSibling(target.getFileName() + META_SUFFIX);
    }

    private void writeMetadataSidecar(Path target, ObjectMetadata metadata) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(MetadataSidecar.from(metadata));
        Path meta = metadataPath(target);
        Path partial = meta.resolveSibling(meta.getFileName() + ".partial." + UUID.randomUUID());
        try {
            Files.write(partial, bytes);
            Files.move(partial, meta, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException retry) {
            Files.move(partial, meta, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ObjectMetadata readMetadataSidecar(Path target) {
        Path meta = metadataPath(target);
        if (!Files.isRegularFile(meta)) {
            return ObjectMetadata.of("application/octet-stream");
        }
        try {
            return objectMapper
                    .readValue(Files.readAllBytes(meta), MetadataSidecar.class)
                    .toObjectMetadata();
        } catch (IOException e) {
            log.warn("Failed to read metadata sidecar {}", meta, e);
            return ObjectMetadata.of("application/octet-stream");
        }
    }

    private String relativize(Path absolute) {
        Path rel = root.relativize(absolute);
        return rel.toString().replace('\\', '/');
    }

    private void safeDelete(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
            pruneEmptyParents(path);
        } catch (IOException e) {
            log.warn("Local delete failed for {}", path, e);
        }
    }

    private void pruneEmptyParents(Path leaf) {
        Path parent = leaf.getParent();
        while (parent != null && !parent.equals(root) && parent.startsWith(root)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(parent)) {
                if (entries.iterator().hasNext()) return;
            } catch (IOException e) {
                return;
            }
            try {
                Files.deleteIfExists(parent);
            } catch (IOException e) {
                return;
            }
            parent = parent.getParent();
        }
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

    /** Recursively removes everything in {@code prefix} (object + metadata + empty dirs). */
    public void deletePrefix(String prefix) {
        Path base = resolve(prefix);
        if (!Files.exists(base)) return;
        if (Files.isRegularFile(base)) {
            delete(prefix);
            return;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            log.warn("Local deletePrefix failed for {}", prefix, e);
        }
    }
}
