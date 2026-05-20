/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

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
            throw new IllegalArgumentException("Image exceeds maximum size of " + (maxBytes / 1024 / 1024) + " MB");
        }

        BufferedImage original = ImageIO.read(new ByteArrayInputStream(data));
        if (original == null) {
            throw new IOException("Unable to read image");
        }

        // Delete old image files before writing new ones
        delete(category, id);

        String extension = extensionFor(contentType);
        Path dir = baseDir.resolve(category.directory()).resolve(id);
        Files.createDirectories(dir);

        int longestSide = Math.max(original.getWidth(), original.getHeight());

        // Store original capped at MAX_PIXEL_SIZE, always compressed
        int originalTarget = Math.min(longestSide, MAX_PIXEL_SIZE);
        writeCompressed(original, dir.resolve("original." + extension), originalTarget, extension);

        // Generate all fixed sizes — always, even if source is smaller
        for (int size : SIZES) {
            int target = Math.min(size, longestSide);
            writeCompressed(original, dir.resolve(size + "." + extension), target, extension);
        }

        // Write content type marker
        Files.writeString(dir.resolve(".content-type"), contentType);
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
        Path dir = baseDir.resolve(category.directory()).resolve(id);
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
        Path dir = baseDir.resolve(category.directory()).resolve(id);
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
        return Files.exists(baseDir.resolve(category.directory()).resolve(id).resolve(".content-type"));
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
            default -> "jpg";
        };
    }

    public record ImageData(byte[] data, String contentType) {}
}
