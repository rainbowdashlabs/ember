/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Per-domain wrapper for instance-scoped logo fragments. Each fragment is one piece of the
 * composite Ember logo (blank base, blink overlays, glow ring, etc.) which the frontend
 * stacks to produce the full mark. Owns a bundled-resource-vs-stored-bytes hash check so
 * re-deploying with the same fragment bytes does not re-write the 5-variant set on every
 * restart.
 *
 * <p>Layout: {@code inst/images/logo-fragments/<name>/<variant>.<ext>}, plus a sibling
 * {@code <name>/.source-hash} marker.
 */
@Singleton
public class LogoFragmentService {
    private static final Variant MARKER = new Variant(".source-hash");

    private final ImageVariantService variants;
    private final StorageService storage;

    @Inject
    public LogoFragmentService(ImageVariantService variants, StorageService storage) {
        this.variants = variants;
        this.storage = storage;
    }

    /**
     * Persists the fragment when its source hash differs from what is already stored. Returns
     * {@code true} when the bytes were written, {@code false} when the stored marker matched.
     */
    public boolean storeIfChanged(String name, byte[] data, String declaredMime) throws IOException {
        String sourceHash = sha256(data);
        if (storedMarker(name)
                .map(stored -> stored.equalsIgnoreCase(sourceHash))
                .orElse(false)) {
            return false;
        }
        variants.store(scope(), StorageCategory.IMAGE_LOGO_FRAGMENT, name, data, declaredMime, 0);
        writeMarker(name, sourceHash);
        return true;
    }

    /** Reads the requested fragment size, falling back to the original when missing. */
    public Optional<ImageVariantService.ImageData> read(String name, int size) {
        return variants.read(scope(), StorageCategory.IMAGE_LOGO_FRAGMENT, name, size);
    }

    private Optional<String> storedMarker(String name) {
        return storage.readAllBytes(scope(), StorageCategory.IMAGE_LOGO_FRAGMENT, name, MARKER)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8).trim());
    }

    private void writeMarker(String name, String hash) {
        storage.store(
                scope(),
                StorageCategory.IMAGE_LOGO_FRAGMENT,
                name,
                MARKER,
                hash.getBytes(StandardCharsets.UTF_8),
                "image/png");
    }

    private StorageScope.Instance scope() {
        return new StorageScope.Instance();
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        }
    }
}
