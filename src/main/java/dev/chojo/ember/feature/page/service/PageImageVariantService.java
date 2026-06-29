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
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

/**
 * Generates and resolves width-keyed image variants. At upload time the service is invoked
 * once per stored original and produces one WebP variant per configured width. The uploaded
 * original is kept in its source format (PNG / JPEG / WebP) under {@code orig.<ext>} as the
 * source-of-truth for download and re-encoding; everything served to in-page consumers is
 * the matching WebP variant.
 *
 * <p>All resizing is done in pure Java via Thumbnailator; WebP encoding shells out to the
 * {@code cwebp} binary (libwebp-tools), which ships in every backend image.
 *
 * <p>Variants are derived cache: regenerable from the original, never counted against the
 * station's quota, and always optional. A missing variant file falls back to the original
 * transparently so a partial generation failure never breaks the page. Stations carried over
 * from earlier releases may still hold legacy {@code w<width>.<png|jpg>} or {@code orig.webp}
 * variants on disk; {@link #readBest} continues to serve them when present so the rollout
 * does not regress already-cached images.
 */
@Singleton
public class PageImageVariantService {
    private static final Logger log = LoggerFactory.getLogger(PageImageVariantService.class);
    private static final String WEBP = "webp";
    private static final String ORIG = "orig";
    private static final int WEBP_QUALITY = 78;

    private final PageFileStorageService storage;
    private final Storage storageConfig;

    @Inject
    public PageImageVariantService(PageFileStorageService storage, Storage storageConfig) {
        this.storage = storage;
        this.storageConfig = storageConfig;
    }

    private static byte[] encodeWebp(BufferedImage image) throws IOException, InterruptedException {
        return WebpEncoder.encode(image, WEBP_QUALITY);
    }

    /**
     * Generates every configured WebP variant for the given image. Silently returns when
     * variants are disabled or the input is not an image we can decode. Failures during
     * generation are logged but never propagated — variant generation is best-effort and the
     * upload itself must not be rolled back if a single resize fails.
     *
     * <p>WebP-source uploads are stored as-is; no further variants are written because the
     * stored original already is a WebP and resized copies would just duplicate it at a smaller
     * footprint that the consuming page rarely needs.
     */
    public void generateVariants(int stationId, String contentHash, byte[] originalBytes, String mimeType) {
        if (!storageConfig.imageVariantsEnabled()) return;
        if (mimeType == null || !mimeType.startsWith("image/")) return;
        if (mimeType.equalsIgnoreCase("image/svg+xml") || mimeType.equalsIgnoreCase("image/gif")) return;
        if (mimeType.equalsIgnoreCase("image/webp")) return;
        if (!storageConfig.imageVariantsWebp() || !WebpEncoder.isAvailable()) return;

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

        for (int width : storageConfig.imageVariantsWidthList()) {
            if (width >= sourceWidth) continue;
            try {
                BufferedImage resized = Thumbnails.of(source).width(width).asBufferedImage();
                byte[] webp = encodeWebp(resized);
                if (webp.length > 0) {
                    storage.storeVariant(stationId, contentHash, "w" + width, WEBP, webp);
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("Variant generation failed station={} hash={} width={}", stationId, contentHash, width, e);
            }
        }
    }

    /**
     * Resolves the best variant for the given (requested width, Accept header). Returns the
     * raw bytes + MIME type. Falls back to the original whenever no variant matches — callers
     * never need to handle a "no variant found" case.
     *
     * <p>Resized variants are WebP-only; clients that do not advertise WebP support always
     * receive the uploaded original. Legacy stations may still hold an {@code orig.webp} from
     * the old layout — it is served when the client advertises WebP and no width was requested.
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
            if (!chosenVariant.equals(ORIG)) {
                var webp = storage.readVariant(stationId, contentHash, chosenVariant, WEBP);
                if (webp.isPresent()) return webp;
            }
            var webpOrig = storage.readVariant(stationId, contentHash, ORIG, WEBP);
            if (webpOrig.isPresent()) return webpOrig;
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
