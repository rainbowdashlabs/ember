/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

/**
 * Handles the multi-size variant set for an image keyed by {@code (scope, category, key)}.
 * Sits above {@link StorageService}: on writes it resizes the source through Thumbnailator into
 * the standard size set and persists every variant; on reads it picks the matching variant,
 * falling back to the original when the requested size is missing. Per-domain image services
 * delegate every byte-level call to this class.
 *
 * <p>Each write produces:
 * <ul>
 *   <li>{@code original.&lt;ext&gt;} capped at {@link #MAX_PIXEL_SIZE} on the longest side, and</li>
 *   <li>One file per entry in {@link #SIZES} ({@code 1024}, {@code 512}, {@code 256}, {@code 128}),
 *       capped at the source's longest side so a small upload still produces every variant.</li>
 * </ul>
 *
 * <p>The extension is derived from the sniffed MIME type — the client-supplied content-type is
 * advisory only. An off-allow-list or unidentifiable upload is rejected before any disk write
 * so an attacker cannot land scriptable content (HTML / SVG / JS) regardless of the claimed
 * MIME header.
 */
@Singleton
public class ImageVariantService {
    /** Maximum pixel dimension (longest side); larger uploads are resized down. */
    public static final int MAX_PIXEL_SIZE = 2048;

    /** Fixed size variants written for every upload (longest side, in pixels). */
    public static final int[] SIZES = {1024, 512, 256, 128};

    /** Variant base name for the (possibly downsized) original. */
    public static final String ORIGINAL_BASE = "original";

    private static final double COMPRESSION_QUALITY = 0.85;
    private static final Logger log = LoggerFactory.getLogger(ImageVariantService.class);

    private final StorageService storage;

    @Inject
    public ImageVariantService(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Persists every variant for an uploaded image. Replaces any existing variants for the
     * same {@code (scope, category, key)} tuple before writing.
     *
     * @param maxBytes upper bound on the raw upload size; {@code 0} disables the check.
     * @throws BadRequestResponse on oversize uploads, MIME-mismatch, or unreadable images.
     * @throws IOException on a disk write failure during variant generation.
     */
    public void store(
            StorageScope scope, StorageCategory category, String key, byte[] data, String declaredMime, int maxBytes)
            throws IOException {
        if (maxBytes > 0 && data.length > maxBytes) {
            throw new BadRequestResponse("Image exceeds maximum size of " + (maxBytes / 1024 / 1024) + " MB");
        }
        String sniffedMime = sniffImageMime(data).orElseThrow(() -> new BadRequestResponse("Unsupported image format"));
        if (!category.acceptsMimeType(sniffedMime)) {
            throw new BadRequestResponse("Unsupported image format");
        }

        BufferedImage original;
        try (var in = new ByteArrayInputStream(data)) {
            original = ImageIO.read(in);
        }
        if (original == null) {
            throw new BadRequestResponse("Unsupported image format");
        }

        delete(scope, category, key);

        String extension = extensionFor(sniffedMime);
        int longestSide = Math.max(original.getWidth(), original.getHeight());

        int originalTarget = Math.min(longestSide, MAX_PIXEL_SIZE);
        byte[] originalBytes = compressTo(original, originalTarget, extension);
        storage.store(scope, category, key, new Variant(ORIGINAL_BASE + "." + extension), originalBytes, sniffedMime);

        for (int size : SIZES) {
            int target = Math.min(size, longestSide);
            byte[] variantBytes = compressTo(original, target, extension);
            storage.store(scope, category, key, new Variant(size + "." + extension), variantBytes, sniffedMime);
        }
    }

    /**
     * Convenience overload that skips the size check.
     */
    public void store(StorageScope scope, StorageCategory category, String key, byte[] data, String declaredMime)
            throws IOException {
        store(scope, category, key, data, declaredMime, 0);
    }

    /**
     * Reads the requested size from a previously stored image, falling back to the original
     * when the requested variant does not exist.
     *
     * @param size requested longest side; {@code 0} returns the original.
     */
    public Optional<ImageData> read(StorageScope scope, StorageCategory category, String key, int size) {
        if (key == null || key.isBlank()) return Optional.empty();
        try {
            String baseName = size > 0 ? String.valueOf(size) : ORIGINAL_BASE;
            Optional<Variant> direct = findVariantByBase(scope, category, key, baseName);
            if (direct.isPresent()) return readVariant(scope, category, key, direct.get());
            if (!baseName.equals(ORIGINAL_BASE)) {
                Optional<Variant> fallback = findVariantByBase(scope, category, key, ORIGINAL_BASE);
                if (fallback.isPresent()) return readVariant(scope, category, key, fallback.get());
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** Whether any variant exists for {@code (scope, category, key)}. */
    public boolean exists(StorageScope scope, StorageCategory category, String key) {
        if (key == null || key.isBlank()) return false;
        try {
            return findVariantByBase(scope, category, key, ORIGINAL_BASE).isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Removes every variant for {@code (scope, category, key)}. No-op when none exist. */
    public void delete(StorageScope scope, StorageCategory category, String key) {
        if (key == null || key.isBlank()) return;
        storage.deletePrefix(scope, category, key);
    }

    private Optional<ImageData> readVariant(StorageScope scope, StorageCategory category, String key, Variant variant) {
        Optional<StoredStream> opt = storage.read(scope, category, key, variant);
        if (opt.isEmpty()) return Optional.empty();
        try (StoredStream stream = opt.get()) {
            byte[] bytes = stream.body().readAllBytes();
            String contentType =
                    contentTypeFor(variant.name(), stream.metadata().contentType());
            return Optional.of(new ImageData(bytes, contentType));
        } catch (IOException e) {
            log.warn("Failed to read image variant scope={} category={} key={}", scope, category, key, e);
            return Optional.empty();
        }
    }

    private Optional<Variant> findVariantByBase(
            StorageScope scope, StorageCategory category, String key, String baseName) {
        List<String> keys = storage.listKeys(scope, category, key);
        for (String entry : keys) {
            int slash = entry.lastIndexOf('/');
            String filename = slash < 0 ? entry : entry.substring(slash + 1);
            int dot = filename.lastIndexOf('.');
            String base = dot < 0 ? filename : filename.substring(0, dot);
            if (base.equals(baseName)) return Optional.of(new Variant(filename));
        }
        return Optional.empty();
    }

    private static byte[] compressTo(BufferedImage source, int longestSide, String extension) throws IOException {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = (double) longestSide / Math.max(w, h);
        int newW = Math.max(1, (int) Math.round(w * scale));
        int newH = Math.max(1, (int) Math.round(h * scale));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(source)
                .size(newW, newH)
                .outputQuality(COMPRESSION_QUALITY)
                .outputFormat(extension)
                .toOutputStream(out);
        return out.toByteArray();
    }

    private static String extensionFor(String mimeType) {
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    private static String contentTypeFor(String filename, String storedContentType) {
        if (storedContentType != null
                && !storedContentType.isBlank()
                && !"application/octet-stream".equals(storedContentType)) {
            return storedContentType;
        }
        int dot = filename.lastIndexOf('.');
        String ext = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    /**
     * Returns the MIME type identified by inspecting the first bytes of {@code data}, or empty
     * when the bytes do not match any allow-listed image signature (PNG / JPEG / WebP / GIF).
     * The client-declared MIME is intentionally not consulted — clients can forge it; magic
     * bytes cannot be forged without also changing the actual format.
     */
    public static Optional<String> sniffImageMime(byte[] data) {
        if (data == null || data.length < 4) return Optional.empty();
        if (startsWith(data, new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return Optional.of("image/png");
        }
        if (startsWith(data, new int[] {0xFF, 0xD8, 0xFF})) {
            return Optional.of("image/jpeg");
        }
        if (data.length >= 12
                && startsWith(data, new int[] {0x52, 0x49, 0x46, 0x46})
                && data[8] == 0x57
                && data[9] == 0x45
                && data[10] == 0x42
                && data[11] == 0x50) {
            return Optional.of("image/webp");
        }
        if (data.length >= 6
                && startsWith(data, new int[] {0x47, 0x49, 0x46, 0x38})
                && (data[4] == 0x37 || data[4] == 0x39)
                && data[5] == 0x61) {
            return Optional.of("image/gif");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int[] signature) {
        if (data.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xff) != signature[i]) return false;
        }
        return true;
    }

    /** Bytes + MIME type returned by {@link #read}. */
    public record ImageData(byte[] data, String contentType) {}
}
