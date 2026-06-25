/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.util.WebpEncoder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Generates and resolves width-keyed image variants. At upload time the
 * service is invoked once per stored original — it produces a resized copy of the original
 * for each configured width, plus a WebP copy when WebP variants are enabled. At read time
 * the service picks the smallest variant ≥ the requested width and, when the client's
 * {@code Accept} header advertises WebP support, prefers the WebP encoding.
 *
 * <p>All resizing is done in pure Java via Thumbnailator; WebP encoding goes through the
 * TwelveMonkeys {@code imageio-webp} writer — no external binaries are required.
 *
 * <p>Variants are derived cache: regenerable from the original, never counted against the
 * station's quota, and always optional. A missing variant file falls back to the original
 * transparently so a partial generation failure never breaks the page.
 */
@Singleton
public class PageImageVariantService {
    private static final Logger log = LoggerFactory.getLogger(PageImageVariantService.class);
    private static final String WEBP = "webp";
    private static final String ORIG = "orig";

    private final PageFileStorageService storage;
    private final Storage storageConfig;

    @Inject
    public PageImageVariantService(PageFileStorageService storage, Storage storageConfig) {
        this.storage = storage;
        this.storageConfig = storageConfig;
    }

    private static byte[] encodeWebp(BufferedImage image) throws IOException, InterruptedException {
        return WebpEncoder.encode(image, Math.round(qualityFor(WEBP) * 100f));
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        String resolved = format.equalsIgnoreCase("jpg") ? "jpeg" : format;
        var writers = ImageIO.getImageWritersByFormatName(resolved);
        if (!writers.hasNext()) {
            throw new IOException("No ImageIO writer for format: " + resolved);
        }
        ImageWriter writer = writers.next();
        var out = new ByteArrayOutputStream(image.getWidth() * image.getHeight() / 4);
        try (var stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = params.getCompressionTypes();
                if (types != null && types.length > 0) {
                    params.setCompressionType(types[0]);
                }
                params.setCompressionQuality(qualityFor(format));
            }
            writer.write(null, new IIOImage(toCompatibleImage(image, format), null, null), params);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static BufferedImage toCompatibleImage(BufferedImage image, String format) {
        boolean wantsOpaque = format.equals("jpg") || format.equals("jpeg");
        if (!wantsOpaque) return image;
        if (image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = copy.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    private static float qualityFor(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> 0.82f;
            case "webp" -> 0.78f;
            default -> 0.9f;
        };
    }

    private static String extensionFor(String mimeType) {
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            default -> "png";
        };
    }

    /**
     * Generates every configured variant for the given image. Silently returns when
     * variants are disabled or the input is not an image we can decode. Failures during
     * generation are logged but never propagated — variant generation is best-effort and the
     * upload itself must not be rolled back if a single resize fails.
     */
    public void generateVariants(int stationId, String contentHash, byte[] originalBytes, String mimeType) {
        if (!storageConfig.imageVariantsEnabled()) return;
        if (mimeType == null || !mimeType.startsWith("image/")) return;
        if (mimeType.equalsIgnoreCase("image/svg+xml") || mimeType.equalsIgnoreCase("image/gif")) return;

        BufferedImage source;
        try (var in = new ByteArrayInputStream(originalBytes)) {
            source = ImageIO.read(in);
        } catch (IOException e) {
            log.warn("Could not decode image for variant generation station={} hash={}", stationId, contentHash, e);
            return;
        }
        if (source == null) {
            log.debug("ImageIO returned null for station={} hash={} type={}", stationId, contentHash, mimeType);
            return;
        }
        int sourceWidth = source.getWidth();
        boolean wantWebp = storageConfig.imageVariantsWebp()
                && !mimeType.equalsIgnoreCase("image/webp")
                && WebpEncoder.isAvailable();
        String origExtension = extensionFor(mimeType);

        for (int width : storageConfig.imageVariantsWidthList()) {
            if (width >= sourceWidth) continue;
            try {
                BufferedImage resized = Thumbnails.of(source).width(width).asBufferedImage();
                if (!mimeType.equalsIgnoreCase("image/webp")) {
                    byte[] encoded = encode(resized, origExtension);
                    if (encoded.length > 0) {
                        storage.storeVariant(stationId, contentHash, "w" + width, origExtension, encoded);
                    }
                }
                if (wantWebp) {
                    byte[] webp = encodeWebp(resized);
                    if (webp.length > 0) {
                        storage.storeVariant(stationId, contentHash, "w" + width, WEBP, webp);
                    }
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("Variant generation failed station={} hash={} width={}", stationId, contentHash, width, e);
            }
        }

        if (wantWebp) {
            try {
                byte[] webpOriginal = encodeWebp(source);
                if (webpOriginal.length > 0) {
                    storage.storeVariant(stationId, contentHash, ORIG, WEBP, webpOriginal);
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("WebP encoding of original failed station={} hash={}", stationId, contentHash, e);
            }
        }
    }

    /**
     * Resolves the best variant for the given (requested width, Accept header). Returns the
     * raw bytes + MIME type. Falls back to the original whenever no variant matches — callers
     * never need to handle a "no variant found" case.
     *
     * @param requestedWidth optional CSS-pixel width the client intends to display the image
     *                       at; {@code null} means "give me the original"
     * @param acceptHeader   the request's {@code Accept} header, used to decide whether the
     *                       client supports WebP
     */
    public Optional<PageFileStorageService.FileData> readBest(
            int stationId, String contentHash, Integer requestedWidth, String acceptHeader) {
        boolean acceptsWebp =
                acceptHeader != null && acceptHeader.toLowerCase(Locale.ROOT).contains("image/webp");
        String chosenVariant = chooseVariantName(requestedWidth);

        if (acceptsWebp && storageConfig.imageVariantsWebp()) {
            var webp = storage.readVariant(stationId, contentHash, chosenVariant, WEBP);
            if (webp.isPresent()) return webp;
            if (!chosenVariant.equals(ORIG)) {
                var webpOrig = storage.readVariant(stationId, contentHash, ORIG, WEBP);
                if (webpOrig.isPresent()) return webpOrig;
            }
        }
        if (!chosenVariant.equals(ORIG)) {
            var resized = storage.readVariant(stationId, contentHash, chosenVariant, null);
            if (resized.isPresent()) return resized;
        }
        return storage.read(stationId, contentHash);
    }

    private String chooseVariantName(Integer requestedWidth) {
        if (requestedWidth == null || requestedWidth <= 0) return ORIG;
        int best = -1;
        for (int width : storageConfig.imageVariantsWidthList()) {
            if (width >= requestedWidth) {
                best = width;
                break;
            }
        }
        return best > 0 ? "w" + best : ORIG;
    }
}
