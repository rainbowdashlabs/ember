/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A byte store, one implementation per protocol. Keys are full keys of the shape
 * {@code <scope>/<category>/<key>[/<variant>]}; the backend never parses them.
 */
public interface StorageBackend extends AutoCloseable {

    StorageBackendType type();

    /**
     * Stores a body with its metadata, atomically: a reader sees the old object or the new one.
     *
     * @param body          drained and closed by the backend
     * @param contentLength the body's size in bytes
     * @throws StorageException when the body could not be stored
     */
    void store(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata);

    /**
     * Stores a body and seals the SHA-256 of what was written into its metadata.
     *
     * @return the metadata as stored, digest included
     */
    default ObjectMetadata storeSealed(String fullKey, InputStream body, long contentLength, ObjectMetadata metadata) {
        var digesting = new DigestingInputStream(body);
        store(fullKey, digesting, contentLength, metadata);
        ObjectMetadata sealed = metadata.withSha256(digesting.hexDigest());
        updateMetadata(fullKey, sealed);
        return sealed;
    }

    /** Replaces the metadata of an existing object without rewriting its bytes. */
    void updateMetadata(String fullKey, ObjectMetadata metadata);

    /**
     * Opens an object for streaming; empty when it does not exist.
     *
     * @throws StorageException when an existing object cannot be read
     */
    Optional<StoredStream> read(String fullKey);

    /** Removes an object and its metadata; nothing happens when it does not exist. */
    void delete(String fullKey);

    boolean exists(String fullKey);

    /** Every full key starting with {@code prefix}, sorted. */
    List<String> listByPrefix(String prefix);

    /** The total size of every object under {@code prefix}. */
    long sumSizeByPrefix(String prefix);

    default Optional<Long> size(String fullKey) {
        return read(fullKey).map(s -> {
            try (var ignored = s) {
                return s.contentLength();
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Writes, reads and removes a marker under {@code _probe/}. */
    HealthStatus probe();

    /** Records a fresh access time; only the local backend keeps them. */
    default void touch(String fullKey) {
        throw new UnsupportedOperationException("touch not supported by " + type());
    }

    default Optional<Instant> lastAccessed(String fullKey) {
        return Optional.empty();
    }

    /** Releases the connections the backend holds. */
    @Override
    default void close() {}
}
