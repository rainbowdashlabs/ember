/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.util.FilePicture;
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
 * <p>A document with pages goes through the same mill on its first page, which is what lets a list of
 * files show a sheet rather than a row of identical paperclips. Its page is filed under names of its
 * own rather than beside the file's variants, and is read only by {@link #readPicture}: left under
 * {@code orig}, {@link #readBest} would hand the drawn page to anybody downloading the document.
 *
 * <p>Variants are derived cache: regenerable from the original, never counted against the
 * station's quota, and always optional. A missing variant file falls back to the original
 * transparently so a partial generation failure never breaks the page. Stations carried over
 * from earlier releases may still hold legacy {@code w<width>.<png|jpg>} or {@code orig.webp}
 * variants on disk; {@link #readBest} continues to serve them when present so the rollout
 * does not regress already-cached images.
 */
@Singleton
public class MediaVariantService {
    private static final Logger log = LoggerFactory.getLogger(MediaVariantService.class);
    private static final String WEBP = "webp";
    private static final String ORIG = "orig";
    private static final String PAGE = "page1";
    private static final String PDF = "application/pdf";
    private static final int WEBP_QUALITY = 78;
    private static final int PDF_RENDER_DPI = 96;

    private final MediaStorageService storage;
    private final Storage storageConfig;

    @Inject
    public MediaVariantService(MediaStorageService storage, Storage storageConfig) {
        this.storage = storage;
        this.storageConfig = storageConfig;
    }

    private static byte[] encodeWebp(BufferedImage image) throws IOException, InterruptedException {
        return WebpEncoder.encode(image, WEBP_QUALITY);
    }

    /** Whether the picture of this kind of file has to be drawn rather than read out of it. */
    private static boolean rendersToPicture(String mimeType) {
        return PDF.equalsIgnoreCase(mimeType);
    }

    /**
     * Whether a file of this kind can be given a picture at all.
     *
     * <p>Asked by whoever serves the picture as well, so that a caller is told there is none rather
     * than being handed the file itself under the name of a thumbnail.
     */
    public static boolean canHavePicture(String mimeType) {
        if (isDrawing(mimeType)) return false;
        return isImage(mimeType) || rendersToPicture(mimeType);
    }

    private static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * What a picture of this kind of file is filed under.
     *
     * <p>A drawn page is filed apart from the file's own variants and never under {@code orig}. The
     * two are read by different callers for different reasons: {@link #readBest} answers with the file
     * and falls back to {@code orig}, so a page left there would be handed out in place of the very
     * document it was drawn from, to every browser that says it takes WebP.
     */
    private static String pictureName(String mimeType, Integer width) {
        String base = rendersToPicture(mimeType) ? PAGE : ORIG;
        if (width == null) return base;
        return base.equals(ORIG) ? "w" + width : PAGE + "-w" + width;
    }

    /**
     * Whether an SVG is what is being asked about.
     *
     * <p>It is an image and a browser draws it, but it is also a document that can carry script, and
     * everything else here refuses to hand one back inline for that reason. A drawing therefore has no
     * picture: the tile says what it is instead, which is what the rest of the application does with
     * one already.
     */
    private static boolean isDrawing(String mimeType) {
        return mimeType != null && mimeType.equalsIgnoreCase("image/svg+xml");
    }

    /** The width a chosen variant name stands for, so a picture can be filed under its own name. */
    private static Integer widthOf(String variantName) {
        if (variantName == null || !variantName.startsWith("w")) return null;
        try {
            return Integer.valueOf(variantName.substring(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Whether a smaller copy of this image is worth making.
     *
     * <p>An animation loses its movement on the way through a still encoder and a drawing has no
     * pixels to save, so all three are served as they are. They are still pictures: this says only
     * that nothing is gained by keeping a second copy.
     */
    private static boolean worthResizing(String mimeType) {
        if (!isImage(mimeType)) return false;
        return !mimeType.equalsIgnoreCase("image/svg+xml")
                && !mimeType.equalsIgnoreCase("image/gif")
                && !mimeType.equalsIgnoreCase("image/webp");
    }

    /**
     * The picture a file's variants are made from.
     *
     * <p>An image is its own, and a document with pages is its first one: that page is what a reader
     * recognises a sheet by, and drawing it is the whole reason a list of files can show what it holds
     * rather than a row of identical paperclips.
     *
     * @return the picture, or null where this file has none
     */
    private BufferedImage sourceImageOf(String mimeType, byte[] bytes, Integer stationId, String contentHash) {
        try {
            if (worthResizing(mimeType)) {
                try (var in = new ByteArrayInputStream(bytes)) {
                    BufferedImage read = ImageIO.read(in);
                    if (read == null) {
                        log.debug(
                                "ImageIO returned null for station={} hash={} type={}",
                                stationId,
                                contentHash,
                                mimeType);
                    }
                    return read;
                }
            }
            if (rendersToPicture(mimeType)) return FilePicture.firstPage(bytes, PDF_RENDER_DPI);
            return null;
        } catch (IOException e) {
            log.warn("Could not read a picture for variants station={} hash={}", stationId, contentHash, e);
            return null;
        }
    }

    private void storeVariant(Integer stationId, String contentHash, String name, BufferedImage image) {
        try {
            byte[] webp = encodeWebp(image);
            if (webp.length > 0) storage.storeVariant(stationId, contentHash, name, WEBP, webp);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Variant generation failed station={} hash={} variant={}", stationId, contentHash, name, e);
        }
    }

    /**
     * Generates every configured WebP variant for the given image. Silently returns when
     * variants are disabled or the input is not an image we can decode. Failures during
     * generation are logged but never propagated - variant generation is best-effort and the
     * upload itself must not be rolled back if a single resize fails.
     *
     * <p>WebP-source uploads are stored as-is; no further variants are written because the
     * stored original already is a WebP and resized copies would just duplicate it at a smaller
     * footprint that the consuming page rarely needs.
     */
    public void generateVariants(Integer stationId, String contentHash, byte[] originalBytes, String mimeType) {
        if (!storageConfig.imageVariantsEnabled()) return;
        if (!storageConfig.imageVariantsWebp() || !WebpEncoder.isAvailable()) return;

        BufferedImage source = sourceImageOf(mimeType, originalBytes, stationId, contentHash);
        if (source == null) return;

        if (rendersToPicture(mimeType)) {
            storeVariant(stationId, contentHash, pictureName(mimeType, null), source);
        }
        int sourceWidth = source.getWidth();

        for (int width : storageConfig.imageVariantsWidthList()) {
            if (width >= sourceWidth) continue;
            try {
                storeVariant(
                        stationId,
                        contentHash,
                        pictureName(mimeType, width),
                        Thumbnails.of(source).width(width).asBufferedImage());
            } catch (IOException e) {
                log.warn("Variant generation failed station={} hash={} width={}", stationId, contentHash, width, e);
            }
        }
    }

    /**
     * Resolves the best variant for the given (requested width, Accept header). Returns the
     * raw bytes + MIME type. Falls back to the original whenever no variant matches - callers
     * never need to handle a "no variant found" case.
     *
     * <p>Resized variants are WebP-only; clients that do not advertise WebP support always
     * receive the uploaded original. Legacy stations may still hold an {@code orig.webp} from
     * the old layout - it is served when the client advertises WebP and no width was requested.
     *
     * @param requestedWidth optional CSS-pixel width the client intends to display the image
     *                       at; {@code null} means "give me the original"
     * @param acceptHeader   the request's {@code Accept} header, used to decide whether the
     *                       client supports WebP
     */
    public Optional<MediaStorageService.FileData> readBest(
            Integer stationId, String contentHash, Integer requestedWidth, String acceptHeader) {
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

    /**
     * The picture of a file, at the width asked for or the nearest kept above it.
     *
     * <p>Unlike {@link #readBest} this never falls back to the file itself. A picture is asked for by
     * something drawing a tile, and handing a document back under that name puts a whole PDF where an
     * image was expected, which the browser draws as nothing at all. Empty means there is no picture,
     * and the caller says what the file is instead.
     */
    public Optional<MediaStorageService.FileData> readPicture(
            Integer stationId, String contentHash, String mimeType, Integer requestedWidth) {
        if (!canHavePicture(mimeType)) return Optional.empty();
        String chosen = chooseVariantName(requestedWidth);
        if (!chosen.equals(ORIG)) {
            var sized = storage.readVariant(stationId, contentHash, pictureName(mimeType, widthOf(chosen)), WEBP);
            if (sized.isPresent()) return sized;
        }
        var full = storage.readVariant(stationId, contentHash, pictureName(mimeType, null), WEBP);
        if (full.isPresent()) return full;
        return isImage(mimeType) ? storage.read(stationId, contentHash) : Optional.empty();
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
