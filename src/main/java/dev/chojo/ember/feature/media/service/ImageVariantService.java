/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.media.image.AcceptedFormats;
import dev.chojo.ember.feature.media.image.ImageEncoder;
import dev.chojo.ember.feature.media.image.ImageFormat;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.image.MediaTypes;
import dev.chojo.ember.feature.media.image.VariantFile;
import dev.chojo.ember.feature.media.image.VariantLayout;
import dev.chojo.ember.feature.media.image.VariantSet;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.util.PixelBudget;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles the multi-size variant set for an image keyed by {@code (scope, category, key)}.
 * Sits above {@link StorageService}: on writes it scales the source into the sizes of its
 * {@link ImageProfile} and persists every one; on reads it picks the stored file that answers the
 * request through {@link VariantSet}. Per-domain image services delegate every byte-level call to
 * this class.
 *
 * <p>Each write produces {@code original.<ext>} capped at the profile's longest side, and one file per
 * size of the profile, capped at the source's longest side. A GIF is kept as it came with no sizes,
 * and a WebP original is kept as it came with its sizes written by {@code cwebp}. The whole set is
 * encoded before the previous one is removed, so an upload that cannot be encoded leaves the previous
 * picture in place.
 *
 * <p>The format is the one the bytes are in, never the one the client declared, and an upload that is
 * none of the accepted formats is rejected before anything is written, so scriptable content cannot
 * land in storage whatever MIME header it came with.
 */
@Singleton
public class ImageVariantService {
    private static final Logger log = LoggerFactory.getLogger(ImageVariantService.class);
    private static final VariantLayout LAYOUT = VariantLayout.SIZED;

    private final StorageService storage;
    private final ImageEncoder encoder;

    @Inject
    public ImageVariantService(StorageService storage, ImageEncoder encoder) {
        this.storage = storage;
        this.encoder = encoder;
    }

    /**
     * The service with the standard encoder, for callers that build it by hand.
     */
    public ImageVariantService(StorageService storage) {
        this(storage, new ImageEncoder());
    }

    /**
     * Persists every variant for an uploaded image, replacing the set stored under the same
     * {@code (scope, category, key)} once the new one is encoded.
     *
     * @param maxBytes upper bound on the raw upload size; {@code 0} disables the check.
     * @throws BadRequestResponse on oversize uploads, MIME-mismatch, or unreadable images.
     * @throws dev.chojo.ember.api.RefusalResponse when the image declares more pixels than
     *                            {@link PixelBudget#MAX_PIXELS}, before any of it is decoded.
     * @throws IOException        when the picture cannot be encoded; nothing stored has changed.
     */
    public void store(
            StorageScope scope, StorageCategory category, String key, byte[] data, String declaredMime, int maxBytes)
            throws IOException {
        if (maxBytes > 0 && data.length > maxBytes) {
            throw new BadRequestResponse("Image exceeds maximum size of " + (maxBytes / 1024 / 1024) + " MB");
        }
        ImageFormat format =
                ImageFormat.sniff(data).orElseThrow(() -> new BadRequestResponse("Unsupported image format"));
        if (!category.acceptsMimeType(format.mimeType())) {
            throw new BadRequestResponse("Unsupported image format");
        }

        BufferedImage original = PixelBudget.read(data);
        if (original == null) {
            throw new BadRequestResponse("Unsupported image format");
        }

        ImageProfile profile = ImageProfile.of(category).orElse(ImageProfile.CONTENT);
        List<Encoded> encoded = encode(profile, format, original, data);

        delete(scope, category, key);
        for (Encoded file : encoded) {
            storage.store(
                    scope,
                    category,
                    key,
                    new Variant(file.name()),
                    file.data(),
                    file.format().mimeType());
        }
    }

    /**
     * Convenience overload that skips the size check.
     */
    public void store(StorageScope scope, StorageCategory category, String key, byte[] data, String declaredMime)
            throws IOException {
        store(scope, category, key, data, declaredMime, 0);
    }

    private List<Encoded> encode(ImageProfile profile, ImageFormat format, BufferedImage image, byte[] data)
            throws IOException {
        var encoded = new ArrayList<Encoded>();
        if (format == ImageFormat.GIF || format == ImageFormat.WEBP) {
            encoded.add(new Encoded(LAYOUT.originalName(format.extension()), data, format));
        } else {
            int side = Math.min(LAYOUT.measure(image), profile.maxOriginalSide());
            encoded.add(new Encoded(
                    LAYOUT.originalName(format.extension()),
                    encoder.encode(LAYOUT.scale(image, side), format),
                    format));
        }
        if (format == ImageFormat.GIF || !encoder.writes(format)) return encoded;
        for (int size : profile.sizes()) {
            int side = Math.min(size, LAYOUT.measure(image));
            encoded.add(new Encoded(
                    LAYOUT.sizeName(size, format), encoder.encode(LAYOUT.scale(image, side), format), format));
        }
        return encoded;
    }

    /**
     * Reads the variant that answers a request for {@code size}: the smallest stored size at or
     * above it, else the original.
     *
     * @param size requested longest side; {@code 0} returns the original.
     */
    public Optional<ImageData> read(StorageScope scope, StorageCategory category, String key, int size) {
        if (key == null || key.isBlank()) return Optional.empty();
        try {
            return set(scope, category, key)
                    .choose(size, AcceptedFormats.EVERY_FORMAT)
                    .flatMap(file -> readVariant(scope, category, key, file));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Whether any variant exists for {@code (scope, category, key)}.
     */
    public boolean exists(StorageScope scope, StorageCategory category, String key) {
        if (key == null || key.isBlank()) return false;
        try {
            return set(scope, category, key).original().isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Removes every variant for {@code (scope, category, key)}. No-op when none exist.
     */
    public void delete(StorageScope scope, StorageCategory category, String key) {
        if (key == null || key.isBlank()) return;
        storage.deletePrefix(scope, category, key);
    }

    private VariantSet set(StorageScope scope, StorageCategory category, String key) {
        return VariantSet.of(LAYOUT, storage.listKeys(scope, category, key));
    }

    private Optional<ImageData> readVariant(
            StorageScope scope, StorageCategory category, String key, VariantFile file) {
        Optional<StoredStream> opt = storage.read(scope, category, key, new Variant(file.fileName()));
        if (opt.isEmpty()) return Optional.empty();
        try (StoredStream stream = opt.get()) {
            byte[] bytes = stream.body().readAllBytes();
            return Optional.of(new ImageData(
                    bytes, MediaTypes.contentTypeOf(file, stream.metadata().contentType())));
        } catch (IOException e) {
            log.warn("Failed to read image variant scope={} category={} key={}", scope, category, key, e);
            return Optional.empty();
        }
    }

    private record Encoded(String name, byte[] data, ImageFormat format) {}

    /**
     * Bytes + MIME type returned by {@link #read}.
     */
    public record ImageData(byte[] data, String contentType) {}
}
