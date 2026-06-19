/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.imageio.ImageIO;

/**
 * Service for processing and storing images on disk at multiple fixed sizes.
 * Images are resized preserving aspect ratio based on their longest side.
 * All images are compressed on write. Every size variant is always generated,
 * even if the source is smaller (re-compressed at original dimensions).
 */
@Singleton
public class ImageService {
    /**
     * Maximum pixel dimension (longest side). Images exceeding this are resized down.
     */
    public static final int MAX_PIXEL_SIZE = 2048;

    /**
     * Fixed size variants to generate (longest side in pixels).
     */
    public static final int[] SIZES = {1024, 512, 256, 128};

    /**
     * JPEG/WebP compression quality (0.0–1.0).
     */
    private static final double COMPRESSION_QUALITY = 0.85;

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);
    private final Path baseDir;

    @Inject
    public ImageService() {
        this.baseDir = Path.of("data", "images");
    }

    /**
     * Stores an uploaded image at all fixed sizes and the original (capped at {@link #MAX_PIXEL_SIZE}).
     * Deletes any previously stored image for this category/id before writing.
     * Every image is compressed. All size variants are always generated; if the source is smaller
     * than a requested size the image is re-compressed at its original dimensions.
     *
     * @param category    image category
     * @param id          unique identifier for the image
     * @param data        raw image bytes
     * @param contentType MIME type
     * @param maxBytes    maximum allowed upload size in bytes (0 to skip check)
     * @throws IOException              if the image cannot be read or written
     * @throws IllegalArgumentException if the upload exceeds {@code maxBytes}
     */
    public void store(ImageCategory category, String id, byte[] data, String contentType, int maxBytes)
            throws IOException {
        if (maxBytes > 0 && data.length > maxBytes) {
            throw new BadRequestResponse("Image exceeds maximum size of " + (maxBytes / 1024 / 1024) + " MB");
        }

        // Sniff the magic bytes — the client-supplied contentType is advisory only.
        // An off-allow-list or unidentifiable upload is rejected before any disk
        // write so an attacker cannot land scriptable content (HTML / SVG / JS)
        // under data/images/ regardless of what MIME they claimed.
        String sniffedMime = sniffImageMime(data).orElseThrow(() -> new BadRequestResponse("Unsupported image format"));

        Path dir = resolveSafe(category, id);
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(data));
        if (original == null) {
            // Every sniffed-allow-listed format is readable by ImageIO once
            // TwelveMonkeys WebP is on the classpath. A null here means a
            // truncated or otherwise unreadable file matching one of the magic
            // signatures — treat it as a bad upload rather than writing raw bytes.
            throw new BadRequestResponse("Unsupported image format");
        }

        delete(category, id);

        String extension = extensionFor(sniffedMime);
        Files.createDirectories(dir);

        int longestSide = Math.max(original.getWidth(), original.getHeight());

        int originalTarget = Math.min(longestSide, MAX_PIXEL_SIZE);
        writeCompressed(original, dir.resolve("original." + extension), originalTarget, extension);

        for (int size : SIZES) {
            int target = Math.min(size, longestSide);
            writeCompressed(original, dir.resolve(size + "." + extension), target, extension);
        }

        Files.writeString(dir.resolve(".content-type"), sniffedMime);
    }

    /**
     * Returns the MIME type identified by inspecting the first bytes of {@code data},
     * or empty when the bytes do not match any allow-listed image signature
     * (PNG / JPEG / WebP / GIF). The client-declared MIME is intentionally not
     * consulted — clients can forge it; magic bytes cannot be forged without
     * also changing the actual format.
     */
    static Optional<String> sniffImageMime(byte[] data) {
        if (data == null || data.length < 4) return Optional.empty();
        if (startsWith(data, new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return Optional.of("image/png");
        }
        if (startsWith(data, new int[] {0xFF, 0xD8, 0xFF})) {
            return Optional.of("image/jpeg");
        }
        if (data.length >= 12
                && startsWith(data, new int[] {0x52, 0x49, 0x46, 0x46}) // "RIFF"
                && data[8] == 0x57
                && data[9] == 0x45
                && data[10] == 0x42
                && data[11] == 0x50) { // "WEBP"
            return Optional.of("image/webp");
        }
        if (data.length >= 6
                && startsWith(data, new int[] {0x47, 0x49, 0x46, 0x38}) // "GIF8"
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

    /**
     * Overload without maxBytes check.
     */
    public void store(ImageCategory category, String id, byte[] data, String contentType) throws IOException {
        store(category, id, data, contentType, 0);
    }

    /**
     * Reads an image at the requested size.
     *
     * @param category image category
     * @param id       image identifier
     * @param size     requested size (one of SIZES), or 0 for original
     * @return image bytes and content type, or empty if not found
     */
    public Optional<ImageData> read(ImageCategory category, String id, int size) {
        Path dir;
        try {
            dir = resolveSafe(category, id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!Files.exists(dir)) {
            return Optional.empty();
        }
        try {
            String contentType = Files.readString(dir.resolve(".content-type")).trim();
            String extension = extensionFor(contentType);
            String filename = size > 0 ? size + "." + extension : "original." + extension;
            Path file = dir.resolve(filename);
            if (!Files.exists(file)) {
                file = dir.resolve("original." + extension);
            }
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            return Optional.of(new ImageData(Files.readAllBytes(file), contentType));
        } catch (IOException e) {
            log.error("Failed to read image {}/{}", category, id, e);
            return Optional.empty();
        }
    }

    /**
     * Deletes all sizes of an image.
     */
    public void delete(ImageCategory category, String id) {
        Path dir = resolveSafe(category, id);
        if (!Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("Failed to delete {}", file, e);
                }
            });
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            log.warn("Failed to clean directory {}", dir, e);
        }
    }

    /**
     * Checks whether an image exists on disk.
     */
    public boolean exists(ImageCategory category, String id) {
        try {
            return Files.exists(resolveSafe(category, id).resolve(".content-type"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Resolves the on-disk directory for an image, rejecting any {@code id} that would
     * escape the category root (e.g. {@code "../etc/passwd"}). Containment is verified
     * after {@link Path#normalize()} so {@code ".."} segments cannot reach outside the
     * configured base directory. Symlinks placed inside the base directory by an
     * operator are not followed; deployments must not stage symlinks inside
     * {@code data/images} that escape the tree.
     *
     * @throws IllegalArgumentException when the resolved path escapes the category root
     *                                  or when the id is null / blank
     */
    private Path resolveSafe(ImageCategory category, String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("invalid image id");
        }
        Path categoryRoot = baseDir.resolve(category.directory()).normalize();
        Path resolved = categoryRoot.resolve(id).normalize();
        if (!resolved.startsWith(categoryRoot) || resolved.equals(categoryRoot)) {
            throw new IllegalArgumentException("invalid image id");
        }
        return resolved;
    }

    private void writeCompressed(BufferedImage source, Path target, int maxSide, String extension) throws IOException {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = (double) maxSide / Math.max(w, h);
        int newW = Math.max(1, (int) Math.round(w * scale));
        int newH = Math.max(1, (int) Math.round(h * scale));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(source)
                .size(newW, newH)
                .outputQuality(COMPRESSION_QUALITY)
                .outputFormat(extension)
                .toOutputStream(out);
        Files.write(target, out.toByteArray());
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    public record ImageData(byte[] data, String contentType) {}
}
