/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import java.io.InputStream;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pluggable byte-store contract. One concrete implementation per protocol
 * ({@code LocalStorageBackend}, {@code SmbStorageBackend}, {@code SftpStorageBackend},
 * {@code S3StorageBackend}). The backend never parses {@code fullKey} — the resolver and
 * {@code StorageService} own the path structure.
 *
 * <p>Streaming is the primary path: byte arrays only enter the picture when the producer
 * genuinely needs to buffer (image resize, gzip, hash) — and even there the buffered bytes
 * are handed to {@link #store(String, InputStream, long, ObjectMetadata)} as a wrapped
 * {@link java.io.ByteArrayInputStream}.
 *
 * <p>Concept §5.1.
 */
public interface StorageBackend extends AutoCloseable {

    /**
     * Discriminator for the kind of backend implementation.
     */
    StorageBackendType type();

    /**
     * Persists {@code body} under {@code fullKey} along with the supplied metadata. The
     * implementation is responsible for atomicity (temp-write then rename) and for
     * round-tripping {@code metadata} so a later {@link #read(String)} sees the same record.
     *
     * @param fullKey       assembled {@code <scope>/<category>/<key>[/<variant>]} key
     * @param body          payload — fully drained and closed by the implementation
     * @param contentLength byte count of the payload; required (no chunked transfer in V1)
     * @param metadata      content type, sha-256, optional filename, optional encoding
     * @throws StorageException on any failure to persist
     */
    void store(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata);

    /**
     * Replaces the metadata sidecar for an existing object without rewriting the body. Used by
     * {@code StorageService} to seal the computed SHA-256 into the sidecar after the upload
     * stream has been fully drained.
     */
    void updateMetadata(String fullKey, ObjectMetadata metadata);

    /**
     * Opens a streaming read of {@code fullKey}. Returns {@link Optional#empty()} when the
     * object does not exist; throws {@link StorageException} on read failures of objects
     * known to exist (corrupted backend state, permission denied, mid-flight network drop).
     */
    Optional<StoredStream> read(String fullKey);

    /**
     * Removes the object and any sidecar metadata. No-op when the object does not exist.
     * Throws {@link StorageException} only on hard errors.
     */
    void delete(String fullKey);

    /**
     * Whether the object exists under {@code fullKey}.
     */
    boolean exists(String fullKey);

    /**
     * Lists every key whose name starts with {@code prefix}. Returned keys are full keys
     * (same shape as the input to {@link #store}), not relative names. Required by image-
     * variant cleanup, ticket-scoped attachment deletion, reconciliation, and migration.
     */
    List<String> listByPrefix(String prefix);

    /**
     * Sums object sizes under {@code prefix}. Default walks {@link #listByPrefix(String)}
     * and queries each key; backends that can answer in one shot (S3 list-with-size) should
     * override.
     */
    default long sumSizeByPrefix(String prefix) {
        long total = 0;
        for (String key : listByPrefix(prefix)) {
            total += size(key).orElse(0L);
        }
        return total;
    }

    /**
     * Returns the on-backend size of {@code fullKey}, when known.
     */
    default Optional<Long> size(String fullKey) {
        return read(fullKey).map(s -> {
            try (var ignored = s) {
                return s.contentLength();
            } catch (Exception e) {
                return null;
            }
        });
    }

    /**
     * Synchronous health check. The bootstrap probe and the runtime health monitor call this
     * to decide whether the backend is usable; implementations write, read and delete a small
     * marker under a reserved {@code _probe/} prefix.
     */
    HealthStatus probe();

    /**
     * Set of optional features this backend supports.
     */
    default Set<BackendCapability> capabilities() {
        return EnumSet.noneOf(BackendCapability.class);
    }

    /**
     * Records a fresh access timestamp on {@code fullKey}. Implementations that do not
     * declare {@link BackendCapability#ACCESS_TIME_TRACKING} throw
     * {@link UnsupportedOperationException}.
     */
    default void touch(String fullKey) {
        throw new UnsupportedOperationException("touch not supported by " + type());
    }

    /**
     * Returns the last-access timestamp, when the backend records one.
     */
    default Optional<Instant> lastAccessed(String fullKey) {
        return Optional.empty();
    }

    /**
     * Releases any I/O resources the backend is holding (SSH session, S3 client, SMB share).
     * The local backend has nothing to close; remote backends release their connection pools.
     */
    @Override
    default void close() {}
}
