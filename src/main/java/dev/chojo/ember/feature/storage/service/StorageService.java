/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.StoredObject;
import dev.chojo.ember.feature.storage.entity.Variant;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Single producer-facing entry point for every byte Ember persists.
 *
 * <p>Producers never reach for {@link java.nio.file.Files} or any backend implementation
 * directly. The façade:
 * <ul>
 *   <li>Resolves the right backend via {@link StorageBackendResolver}.</li>
 *   <li>Validates the supplied MIME type against {@link StorageCategory#acceptedMimeTypes()}.</li>
 *   <li>Drives the SHA-256 digest as the bytes stream through.</li>
 *   <li>Applies the per-category POSIX mode after every successful write (local backend only).</li>
 * </ul>
 *
 * <p>Domain-specific concerns (image resize, gzip, magic-byte sniffing) live in small helpers
 * above this layer. {@code StorageService} stores whatever bytes it is given.
 */
@Singleton
public class StorageService {
    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final StorageBackendResolver resolver;
    private final StationRepository stationRepository;
    private final InstanceStorageReadOnlyState instanceReadOnly;

    @Inject
    public StorageService(
            StorageBackendResolver resolver,
            LocalStorageBackend localBackend,
            StationRepository stationRepository,
            InstanceStorageReadOnlyState instanceReadOnly) {
        this.resolver = resolver;
        this.stationRepository = stationRepository;
        this.instanceReadOnly = instanceReadOnly;
    }

    /**
     * Convenience constructor for tests that do not need the read-only-for-transfer / instance
     * read-only gates. Every store call proceeds.
     */
    public StorageService(StorageBackendResolver resolver, LocalStorageBackend localBackend) {
        this(resolver, localBackend, null, null);
    }

    private static void validateMime(StorageCategory category, String mimeHint) {
        if (category.acceptedMimeTypes() == StorageCategory.MIME_ANY) return;
        if (mimeHint == null) {
            throw new IllegalArgumentException("Category " + category + " requires a MIME hint");
        }
        if (!category.acceptsMimeType(mimeHint)) {
            throw new IllegalArgumentException("MIME type " + mimeHint + " not accepted for category " + category);
        }
    }

    /**
     * Persists {@code body} under {@code (scope, category, key)}. The returned record carries
     * the SHA-256 digest computed while the bytes flowed into the backend.
     */
    public StoredObject store(
            StorageScope scope,
            StorageCategory category,
            String key,
            Variant variant,
            InputStream body,
            long contentLength,
            String mimeHint) {
        validateMime(category, mimeHint);
        guardInstanceReadOnly();
        guardReadOnlyForTransfer(scope);
        StorageBackend backend = resolver.forScope(scope, category);
        String fullKey = fullKey(scope, category, key, variant);
        DigestingInputStream wrapped = new DigestingInputStream(body);
        ObjectMetadata initial = ObjectMetadata.of(mimeHint == null ? "application/octet-stream" : mimeHint);
        backend.store(fullKey, wrapped, contentLength, initial);
        ObjectMetadata sealed = initial.withSha256(wrapped.hexDigest());
        backend.updateMetadata(fullKey, sealed);
        applyPosixMode(backend, fullKey, category);
        log.info(
                "Stored file scope={} category={} key={} variant={} size={}",
                scope,
                category,
                key,
                variant,
                contentLength);
        return new StoredObject(scope, category, key, sealed, contentLength);
    }

    /**
     * Variant-less store; the implicit {@link Variant#ORIGINAL} is used.
     */
    public void store(
            StorageScope scope,
            StorageCategory category,
            String key,
            InputStream body,
            long contentLength,
            String mimeHint) {
        store(scope, category, key, Variant.ORIGINAL, body, contentLength, mimeHint);
    }

    /**
     * Byte-array convenience overload that buffers the payload before delegating.
     */
    public StoredObject store(StorageScope scope, StorageCategory category, String key, byte[] bytes, String mimeHint) {
        return store(scope, category, key, Variant.ORIGINAL, new ByteArrayInputStream(bytes), bytes.length, mimeHint);
    }

    /**
     * Byte-array convenience overload with explicit variant.
     */
    public void store(
            StorageScope scope, StorageCategory category, String key, Variant variant, byte[] bytes, String mimeHint) {
        store(scope, category, key, variant, new ByteArrayInputStream(bytes), bytes.length, mimeHint);
    }

    /**
     * Opens a streaming read of the requested {@code (scope, category, key, variant)} tuple.
     * Touches the access-time when the category is {@link StorageCategory#isAccessTimeLru()}
     * and the resolved backend declares the matching capability.
     */
    public Optional<StoredStream> read(StorageScope scope, StorageCategory category, String key, Variant variant) {
        StorageBackend backend = resolver.forScope(scope, category);
        String fullKey = fullKey(scope, category, key, variant);
        Optional<StoredStream> stream = backend.read(fullKey);
        if (stream.isPresent() && category.isAccessTimeLru()) {
            try {
                backend.touch(fullKey);
            } catch (UnsupportedOperationException ignored) {
            }
        }
        return stream;
    }

    /**
     * Variant-less {@link #read(StorageScope, StorageCategory, String, Variant)}.
     */
    public Optional<StoredStream> read(StorageScope scope, StorageCategory category, String key) {
        return read(scope, category, key, Variant.ORIGINAL);
    }

    /**
     * Reads the entire object into a byte array. Convenience for callers that already need
     * the bytes in heap (image resize, hashing); not for streaming downloads.
     */
    public Optional<byte[]> readAllBytes(StorageScope scope, StorageCategory category, String key, Variant variant) {
        Optional<StoredStream> opt = read(scope, category, key, variant);
        if (opt.isEmpty()) return Optional.empty();
        try (StoredStream stream = opt.get()) {
            return Optional.of(stream.body().readAllBytes());
        } catch (IOException e) {
            throw new StorageException("Reading bytes failed for " + category + " key=" + key, e);
        }
    }

    /**
     * Variant-less convenience that buffers the entire object.
     */
    public Optional<byte[]> readAllBytes(StorageScope scope, StorageCategory category, String key) {
        return readAllBytes(scope, category, key, Variant.ORIGINAL);
    }

    /**
     * Deletes a single object at {@code (scope, category, key, variant)}.
     */
    public void delete(StorageScope scope, StorageCategory category, String key, Variant variant) {
        StorageBackend backend = resolver.forScope(scope, category);
        String fullKey = fullKey(scope, category, key, variant);
        backend.delete(fullKey);
        log.info("Deleted file scope={} category={} key={} variant={}", scope, category, key, variant);
    }

    /**
     * Variant-less {@link #delete(StorageScope, StorageCategory, String, Variant)}.
     */
    public void delete(StorageScope scope, StorageCategory category, String key) {
        delete(scope, category, key, Variant.ORIGINAL);
    }

    /**
     * Deletes every object whose key starts with {@code keyPrefix} within the given
     * {@code (scope, category)}. Used by image-variant cleanup, board-attachment ticket
     * cleanup, and on-station-delete sweeps.
     */
    public void deletePrefix(StorageScope scope, StorageCategory category, String keyPrefix) {
        StorageBackend backend = resolver.forScope(scope, category);
        String fullPrefix = scope.prefix() + "/" + category.prefix();
        if (keyPrefix != null && !keyPrefix.isEmpty()) {
            fullPrefix = fullPrefix + "/" + keyPrefix;
        }
        if (backend instanceof LocalStorageBackend local) {
            local.deletePrefix(fullPrefix);
            return;
        }
        for (String key : backend.listByPrefix(fullPrefix)) {
            backend.delete(key);
        }
    }

    /**
     * Whether an object exists at {@code (scope, category, key, variant)}.
     */
    public boolean exists(StorageScope scope, StorageCategory category, String key, Variant variant) {
        StorageBackend backend = resolver.forScope(scope, category);
        return backend.exists(fullKey(scope, category, key, variant));
    }

    /**
     * Variant-less {@link #exists(StorageScope, StorageCategory, String, Variant)}.
     */
    public boolean exists(StorageScope scope, StorageCategory category, String key) {
        return exists(scope, category, key, Variant.ORIGINAL);
    }

    /**
     * Streaming read by category-relative key - the inverse of {@link #listKeys}. Unlike
     * {@link #read(StorageScope, StorageCategory, String)} this does not append the
     * {@code original} variant suffix, so callers can address variant rows directly
     * (e.g. the entries returned by {@code listKeys} for image categories).
     */
    public Optional<StoredStream> readRelative(StorageScope scope, StorageCategory category, String relativeKey) {
        StorageBackend backend = resolver.forScope(scope, category);
        String fullKey = scope.prefix() + "/" + category.prefix() + "/" + relativeKey;
        return backend.read(fullKey);
    }

    /**
     * Lists keys under a producer-chosen prefix; returns producer-relative keys.
     */
    public List<String> listKeys(StorageScope scope, StorageCategory category, String keyPrefix) {
        StorageBackend backend = resolver.forScope(scope, category);
        String categoryPrefix = scope.prefix() + "/" + category.prefix();
        String fullPrefix =
                keyPrefix == null || keyPrefix.isEmpty() ? categoryPrefix : categoryPrefix + "/" + keyPrefix;
        return backend.listByPrefix(fullPrefix).stream()
                .map(full -> full.substring(categoryPrefix.length() + 1))
                .toList();
    }

    /**
     * Returns the total bytes under {@code (scope, category)}. Used by reconciliation.
     */
    public long sumSize(StorageScope scope, StorageCategory category) {
        StorageBackend backend = resolver.forScope(scope, category);
        String prefix = scope.prefix() + "/" + category.prefix();
        return backend.sumSizeByPrefix(prefix);
    }

    /**
     * Reads the last-access timestamp for {@code (scope, category, key)}, when supported.
     */
    public Optional<Instant> lastAccessed(StorageScope scope, StorageCategory category, String key) {
        StorageBackend backend = resolver.forScope(scope, category);
        return backend.lastAccessed(fullKey(scope, category, key, Variant.ORIGINAL));
    }

    /**
     * Assembles a full backend key from its parts: {@code <scope>/<category>/<key>[/<variant>]}.
     * Visible for tests and for producers (such as the legacy page-image variant code) that
     * still need to address the underlying storage layout while migrating.
     */
    public String fullKey(StorageScope scope, StorageCategory category, String key, Variant variant) {
        StringBuilder sb = new StringBuilder();
        sb.append(scope.prefix()).append('/').append(category.prefix());
        if (key != null && !key.isEmpty()) sb.append('/').append(key);
        if (variant != null && !variant.isOriginal()) sb.append('/').append(variant.name());
        return sb.toString();
    }

    private void applyPosixMode(StorageBackend backend, String fullKey, StorageCategory category) {
        if (category.posixMode() == null) return;
        if (!(backend instanceof LocalStorageBackend local)) return;
        local.applyPosixMode(fullKey, category.posixMode());
    }

    /**
     * Refuses writes to a station that has been flagged read-only for an in-flight transfer.
     * Other scopes (instance, account) pass straight through.
     */
    private void guardReadOnlyForTransfer(StorageScope scope) {
        if (stationRepository == null) return;
        if (!(scope instanceof StorageScope.Station station)) return;
        if (stationRepository.isReadOnlyForTransfer(station.stationId())) {
            throw new StationReadOnlyForTransferException(station.stationId());
        }
    }

    /**
     * Refuses every write while an instance-wide storage backend migration is in flight, so
     * the byte-copy loop and the resolver flip do not race with concurrent uploads.
     */
    private void guardInstanceReadOnly() {
        if (instanceReadOnly == null) return;
        if (instanceReadOnly.isLocked()) {
            throw new InstanceReadOnlyForMigrationException();
        }
    }

    /**
     * {@link InputStream} wrapper that computes SHA-256 as bytes flow through. The digest is
     * read once the underlying stream is fully drained - typically right after the backend's
     * {@code store(...)} call returns.
     */
    private static final class DigestingInputStream extends InputStream {
        private final InputStream delegate;
        private final MessageDigest digest;

        DigestingInputStream(InputStream delegate) {
            this.delegate = delegate;
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) digest.update((byte) b);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) digest.update(b, off, n);
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        String hexDigest() {
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
