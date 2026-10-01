/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.Refusal;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The one entry point for every byte Ember persists. It resolves the backend, checks the MIME type
 * against the category, seals the SHA-256 and applies the category's POSIX mode; it stores whatever
 * bytes it is given, and resizing, compressing or sniffing happen above it.
 */
@Singleton
public class StorageService {
    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final StorageBackendResolver resolver;
    private final @Nullable StationRepository stationRepository;
    private final @Nullable InstanceStorageReadOnlyState instanceReadOnly;

    @Inject
    public StorageService(
            StorageBackendResolver resolver,
            LocalStorageBackend localBackend,
            StationRepository stationRepository,
            InstanceStorageReadOnlyState instanceReadOnly) {
        this(resolver, stationRepository, instanceReadOnly);
    }

    /** A service without the read-only gates, for tests. */
    public StorageService(StorageBackendResolver resolver, LocalStorageBackend localBackend) {
        this(resolver, null, null);
    }

    private StorageService(
            StorageBackendResolver resolver,
            @Nullable StationRepository stationRepository,
            @Nullable InstanceStorageReadOnlyState instanceReadOnly) {
        this.resolver = resolver;
        this.stationRepository = stationRepository;
        this.instanceReadOnly = instanceReadOnly;
    }

    private static void validateMime(StorageCategory category, @Nullable String mimeHint) {
        if (category.acceptedMimeTypes() == StorageCategory.MIME_ANY) return;
        if (mimeHint == null) {
            throw new IllegalArgumentException("Category " + category + " requires a MIME hint");
        }
        if (!category.acceptsMimeType(mimeHint)) {
            throw new IllegalArgumentException("MIME type " + mimeHint + " not accepted for category " + category);
        }
    }

    /** Stores a body and answers with the SHA-256 computed while it streamed. */
    public StoredObject store(
            StorageScope scope,
            StorageCategory category,
            String key,
            Variant variant,
            InputStream body,
            long contentLength,
            @Nullable String mimeHint) {
        validateMime(category, mimeHint);
        guardInstanceReadOnly();
        guardReadOnlyForTransfer(scope);
        StorageBackend backend = resolver.forScope(scope, category);
        String fullKey = fullKey(scope, category, key, variant);
        ObjectMetadata initial = ObjectMetadata.of(Objects.requireNonNullElse(mimeHint, "application/octet-stream"));
        ObjectMetadata sealed = backend.storeSealed(fullKey, body, contentLength, initial);
        if (backend instanceof LocalStorageBackend local) {
            category.posixMode().ifPresent(mode -> local.applyPosixMode(fullKey, mode));
        }
        log.info(
                "Stored file scope={} category={} key={} variant={} size={}",
                scope,
                category,
                key,
                variant,
                contentLength);
        return new StoredObject(scope, category, key, sealed, contentLength);
    }

    public void store(
            StorageScope scope,
            StorageCategory category,
            String key,
            InputStream body,
            long contentLength,
            @Nullable String mimeHint) {
        store(scope, category, key, Variant.ORIGINAL, body, contentLength, mimeHint);
    }

    public StoredObject store(
            StorageScope scope, StorageCategory category, String key, byte[] bytes, @Nullable String mimeHint) {
        return store(scope, category, key, Variant.ORIGINAL, new ByteArrayInputStream(bytes), bytes.length, mimeHint);
    }

    public void store(
            StorageScope scope,
            StorageCategory category,
            String key,
            Variant variant,
            byte[] bytes,
            @Nullable String mimeHint) {
        store(scope, category, key, variant, new ByteArrayInputStream(bytes), bytes.length, mimeHint);
    }

    /** Opens an object for streaming, and records the access when the category is evicted by it. */
    public Optional<StoredStream> read(StorageScope scope, StorageCategory category, String key, Variant variant) {
        StorageBackend backend = resolver.forScope(scope, category);
        String fullKey = fullKey(scope, category, key, variant);
        Optional<StoredStream> stream = backend.read(fullKey);
        if (stream.isPresent() && category.isAccessTimeLru()) {
            try {
                backend.touch(fullKey);
            } catch (UnsupportedOperationException _) {
                log.debug("Backend of {} cannot record access times, {} keeps its old one", category, fullKey);
            }
        }
        return stream;
    }

    public Optional<StoredStream> read(StorageScope scope, StorageCategory category, String key) {
        return read(scope, category, key, Variant.ORIGINAL);
    }

    /** Reads a whole object into memory, for callers that need the bytes anyway; not for downloads. */
    public Optional<byte[]> readAllBytes(StorageScope scope, StorageCategory category, String key, Variant variant) {
        Optional<StoredStream> opt = read(scope, category, key, variant);
        if (opt.isEmpty()) return Optional.empty();
        try (StoredStream stream = opt.get()) {
            return Optional.of(stream.body().readAllBytes());
        } catch (IOException e) {
            throw new StorageException("Reading bytes failed for " + category + " key=" + key, e);
        }
    }

    public Optional<byte[]> readAllBytes(StorageScope scope, StorageCategory category, String key) {
        return readAllBytes(scope, category, key, Variant.ORIGINAL);
    }

    public void delete(StorageScope scope, StorageCategory category, String key, Variant variant) {
        StorageBackend backend = resolver.forScope(scope, category);
        String fullKey = fullKey(scope, category, key, variant);
        backend.delete(fullKey);
        log.info("Deleted file scope={} category={} key={} variant={}", scope, category, key, variant);
    }

    public void delete(StorageScope scope, StorageCategory category, String key) {
        delete(scope, category, key, Variant.ORIGINAL);
    }

    /** Deletes every object whose key starts with {@code keyPrefix}; the whole category when it is empty. */
    public void deletePrefix(StorageScope scope, StorageCategory category, String keyPrefix) {
        StorageBackend backend = resolver.forScope(scope, category);
        String fullPrefix = prefixed(scope, category, keyPrefix);
        if (backend instanceof LocalStorageBackend local) {
            local.deletePrefix(fullPrefix);
            log.info("Deleted everything under {}", fullPrefix);
            return;
        }
        List<String> keys = backend.listByPrefix(fullPrefix);
        for (String key : keys) {
            backend.delete(key);
        }
        log.info("Deleted {} object(s) under {}", keys.size(), fullPrefix);
    }

    public boolean exists(StorageScope scope, StorageCategory category, String key, Variant variant) {
        StorageBackend backend = resolver.forScope(scope, category);
        return backend.exists(fullKey(scope, category, key, variant));
    }

    public boolean exists(StorageScope scope, StorageCategory category, String key) {
        return exists(scope, category, key, Variant.ORIGINAL);
    }

    /**
     * Opens an object by the category-relative key {@link #listKeys} returns, which names variants
     * directly instead of adding one.
     */
    public Optional<StoredStream> readRelative(StorageScope scope, StorageCategory category, String relativeKey) {
        return resolver.forScope(scope, category).read(prefixed(scope, category, null) + "/" + relativeKey);
    }

    /** Whether an object exists under a category-relative key, the form {@link #listKeys} returns. */
    public boolean existsRelative(StorageScope scope, StorageCategory category, String relativeKey) {
        return resolver.forScope(scope, category).exists(prefixed(scope, category, null) + "/" + relativeKey);
    }

    /** The category-relative keys under a prefix. */
    public List<String> listKeys(StorageScope scope, StorageCategory category, String keyPrefix) {
        StorageBackend backend = resolver.forScope(scope, category);
        String categoryPrefix = prefixed(scope, category, null);
        return backend.listByPrefix(prefixed(scope, category, keyPrefix)).stream()
                .map(full -> full.substring(categoryPrefix.length() + 1))
                .toList();
    }

    public long sumSize(StorageScope scope, StorageCategory category) {
        return resolver.forScope(scope, category).sumSizeByPrefix(prefixed(scope, category, null));
    }

    public Optional<Instant> lastAccessed(StorageScope scope, StorageCategory category, String key) {
        StorageBackend backend = resolver.forScope(scope, category);
        return backend.lastAccessed(fullKey(scope, category, key, Variant.ORIGINAL));
    }

    /** The backend key {@code <scope>/<category>/<key>[/<variant>]}. */
    public String fullKey(StorageScope scope, StorageCategory category, String key, Variant variant) {
        String fullKey = prefixed(scope, category, key);
        return variant != null && !variant.isOriginal() ? fullKey + "/" + variant.name() : fullKey;
    }

    private static String prefixed(StorageScope scope, StorageCategory category, @Nullable String key) {
        String categoryPrefix = scope.prefix() + "/" + category.prefix();
        return key == null || key.isEmpty() ? categoryPrefix : categoryPrefix + "/" + key;
    }

    /** Refuses writes to a station flagged read-only for a transfer. */
    private void guardReadOnlyForTransfer(StorageScope scope) {
        var stations = stationRepository;
        if (stations == null) return;
        if (!(scope instanceof StorageScope.Station station)) return;
        if (stations.isReadOnlyForTransfer(station.stationId())) {
            throw Refusal.STATION_READ_ONLY_FOR_FILE_WRITE.raise();
        }
    }

    /** Refuses every write while an instance-wide migration copies the bytes. */
    private void guardInstanceReadOnly() {
        var readOnly = instanceReadOnly;
        if (readOnly == null) return;
        if (readOnly.isLocked()) {
            throw Refusal.INSTANCE_STORAGE_MOVING.raise();
        }
    }
}
