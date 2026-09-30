/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.media.image.AcceptedFormats;
import dev.chojo.ember.feature.media.image.ImageEncoder;
import dev.chojo.ember.feature.media.image.ImageFormat;
import dev.chojo.ember.feature.media.image.VariantLayout;
import dev.chojo.ember.util.FilePicture;
import dev.chojo.ember.util.PixelBudget;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;

/**
 * Generates and resolves width-keyed image variants. At upload time the service is invoked
 * once per stored original and produces one WebP variant per configured width. The uploaded
 * original is kept in its source format (PNG / JPEG / WebP) under {@code orig.<ext>} as the
 * source-of-truth for download and re-encoding; everything served to in-page consumers is
 * the matching WebP variant.
 *
 * <p>Resizing is done in pure Java via Thumbnailator; WebP encoding shells out to the
 * {@code cwebp} binary (libwebp-tools), which ships in every backend image.
 *
 * <p>A document with pages goes through the same mill on its first page, which is what lets a list of
 * files show a sheet rather than a row of identical paperclips. Its page is filed under names of its
 * own ({@link VariantLayout#LIBRARY_PAGE}) rather than beside the file's variants, and is read only by
 * {@link #readPicture}: left under {@code orig}, {@link #readBest} would hand the drawn page to anybody
 * downloading the document.
 *
 * <p>Variants are derived cache: regenerable from the original, never counted against the
 * station's quota, and always optional. A missing variant file falls back to the original
 * transparently so a partial generation failure never breaks the page.
 */
@Singleton
public class MediaVariantService {
    private static final Logger log = LoggerFactory.getLogger(MediaVariantService.class);
    private static final String PDF = "application/pdf";
    private static final int PDF_RENDER_DPI = 96;

    private final MediaStorageService storage;
    private final Storage storageConfig;
    private final ImageEncoder encoder;

    @Inject
    public MediaVariantService(MediaStorageService storage, Storage storageConfig, ImageEncoder encoder) {
        this.storage = storage;
        this.storageConfig = storageConfig;
        this.encoder = encoder;
    }

    /**
     * The service with the standard encoder, for callers that build it by hand.
     */
    public MediaVariantService(MediaStorageService storage, Storage storageConfig) {
        this(storage, storageConfig, new ImageEncoder());
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
     * The layout a picture of this kind of file is filed in.
     *
     * <p>A drawn page is filed apart from the file's own variants and never under {@code orig}. The
     * two are read by different callers for different reasons: {@link #readBest} answers with the file
     * and falls back to {@code orig}, so a page left there would be handed out in place of the very
     * document it was drawn from, to every browser that says it takes WebP.
     */
    private static VariantLayout pictureLayout(String mimeType) {
        return rendersToPicture(mimeType) ? VariantLayout.LIBRARY_PAGE : VariantLayout.LIBRARY;
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
                if (!PixelBudget.fits(bytes)) {
                    log.warn(
                            "Skipped variants of an image above the pixel budget station={} hash={}",
                            stationId,
                            contentHash);
                    return null;
                }
                BufferedImage read = PixelBudget.read(bytes);
                if (read == null) {
                    log.debug("No image reader for station={} hash={} type={}", stationId, contentHash, mimeType);
                }
                return read;
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
            byte[] webp = encoder.encode(image, ImageFormat.WEBP);
            if (webp.length > 0) storage.storeFile(stationId, contentHash, name, webp);
        } catch (IOException e) {
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
        if (!storageConfig.imageVariantsWebp() || !encoder.writes(ImageFormat.WEBP)) return;

        BufferedImage source = sourceImageOf(mimeType, originalBytes, stationId, contentHash);
        if (source == null) return;

        VariantLayout layout = pictureLayout(mimeType);
        if (rendersToPicture(mimeType)) {
            storeVariant(stationId, contentHash, layout.originalName(ImageFormat.WEBP.extension()), source);
        }
        int sourceWidth = layout.measure(source);

        for (int width : storageConfig.imageVariantsWidthList()) {
            if (width >= sourceWidth) continue;
            try {
                storeVariant(
                        stationId, contentHash, layout.sizeName(width, ImageFormat.WEBP), layout.scale(source, width));
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
     * <p>Resized variants are WebP-only; clients that do not advertise WebP support, and requests
     * without a width, always receive the uploaded original.
     *
     * @param requestedWidth optional CSS-pixel width the client intends to display the image
     *                       at; {@code null} means "give me the original"
     * @param acceptHeader   the request's {@code Accept} header, used to decide whether the
     *                       client supports WebP
     */
    public Optional<MediaStorageService.FileData> readBest(
            Integer stationId, String contentHash, Integer requestedWidth, String acceptHeader) {
        AcceptedFormats accepted = storageConfig.imageVariantsWebp()
                ? AcceptedFormats.fromAcceptHeader(acceptHeader)
                : AcceptedFormats.WITHOUT_WEBP;
        return storage.set(stationId, contentHash, VariantLayout.LIBRARY)
                .choose(widthOf(requestedWidth), accepted)
                .flatMap(file -> storage.readFile(stationId, contentHash, file));
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
        var pictures = storage.set(stationId, contentHash, pictureLayout(mimeType));
        var chosen = pictures.size(widthOf(requestedWidth), AcceptedFormats.EVERY_FORMAT)
                .or(() -> pictures.original(ImageFormat.WEBP));
        if (isImage(mimeType)) chosen = chosen.or(pictures::original);
        return chosen.flatMap(file -> storage.readFile(stationId, contentHash, file));
    }

    private static int widthOf(Integer requestedWidth) {
        return requestedWidth == null ? 0 : requestedWidth;
    }
}
