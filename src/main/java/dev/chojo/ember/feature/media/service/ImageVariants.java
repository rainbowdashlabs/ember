/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.media.entity.MediaContent;
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
import dev.chojo.ember.util.FilePicture;
import dev.chojo.ember.util.PixelBudget;
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
 * The one pipeline every stored picture goes through: sniff, measure, decode once, encode every size
 * of its {@link ImageProfile}, then write; and the one chooser every read goes through.
 *
 * <p>A picture set lives under {@code (scope, category, key)}, and its files are named by the
 * profile's {@link VariantLayout}. The sized families (avatars, logos, lost and found, quiz, wiki
 * pictures) hand the whole upload to {@link #store}, which replaces the previous set only once the new
 * one is fully encoded. The media library keeps its original itself, deduplicated by content hash, and
 * asks {@link #addSizes} for the smaller copies and the drawn first page of a document, which are
 * derived and best effort: a copy that cannot be made is logged and the upload stands.
 *
 * <p>Format rules: a GIF is kept as it came and never resized, since a resize keeps only its first
 * frame. A WebP original is kept as it came, since only {@code cwebp} writes WebP; its sizes are made
 * by {@code cwebp}, and where the host has none only the original is kept. A drawing (SVG) is never
 * taken by a sized family and has no picture in the library.
 */
@Singleton
public class ImageVariants {
    private static final Logger log = LoggerFactory.getLogger(ImageVariants.class);
    private static final String PDF = "application/pdf";
    private static final String SVG = "image/svg+xml";
    private static final int PDF_RENDER_DPI = 96;

    private final StorageService storage;
    private final Storage config;
    private final ImageEncoder encoder;

    @Inject
    public ImageVariants(StorageService storage, Storage config, ImageEncoder encoder) {
        this.storage = storage;
        this.config = config;
        this.encoder = encoder;
    }

    /**
     * The pipeline with the default settings and the standard encoder, for callers that build it by
     * hand.
     */
    public ImageVariants(StorageService storage) {
        this(storage, new Storage(), new ImageEncoder());
    }

    /**
     * Whether a library file of this kind can be given a picture at all.
     *
     * <p>Asked by whoever serves the picture as well, so that a caller is told there is none rather
     * than being handed the file itself under the name of a thumbnail. An SVG is an image a browser
     * draws, but also a document that can carry script, so it has none.
     *
     * @param mimeType the stored type of the file
     * @return true for raster images and PDFs
     */
    public static boolean canHavePicture(String mimeType) {
        if (mimeType == null || mimeType.equalsIgnoreCase(SVG)) return false;
        return mimeType.startsWith("image/") || isDocument(mimeType);
    }

    private static boolean isDocument(String mimeType) {
        return PDF.equalsIgnoreCase(mimeType);
    }

    /**
     * Stores an uploaded picture as the whole set of a sized family, replacing the set stored under
     * the same key once the new one is encoded.
     *
     * @param profile  the family, one of the sized ones
     * @param maxBytes upper bound on the raw upload size; {@code 0} disables the check
     * @throws dev.chojo.ember.api.RefusalResponse when the upload is too large, is none of the
     *                                             formats the category takes, cannot be decoded, or
     *                                             declares more pixels than {@link PixelBudget#MAX_PIXELS}
     * @throws IOException                         when the picture cannot be encoded; nothing stored has
     *                                             changed
     */
    public void store(
            ImageProfile profile, StorageScope scope, StorageCategory category, String key, byte[] data, int maxBytes)
            throws IOException {
        if (profile.layout() != VariantLayout.SIZED) {
            throw new IllegalArgumentException("The library keeps its own originals; add sizes to them instead");
        }
        if (maxBytes > 0 && data.length > maxBytes) throw Refusal.PICTURE_TOO_LARGE.raise();
        ImageFormat format = ImageFormat.sniff(data)
                .filter(sniffed -> category.acceptsMimeType(sniffed.mimeType()))
                .orElseThrow(Refusal.PICTURE_KIND_NOT_TAKEN::raise);
        BufferedImage image = decode(data);

        var encoded = new ArrayList<Encoded>();
        encoded.add(original(profile, format, image, data));
        encoded.addAll(sizes(profile, format, image));

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
     * Makes the smaller copies of a library file that is already stored, and the drawn first page of a
     * document. Best effort: nothing is thrown, and whatever cannot be made is logged and left out.
     *
     * @param mimeType the stored type of the file
     */
    public void addSizes(StorageScope scope, StorageCategory category, String key, byte[] data, String mimeType) {
        if (!config.imageVariantsEnabled() || !config.imageVariantsWebp() || !encoder.writes(ImageFormat.WEBP)) return;
        if (!canHavePicture(mimeType)) return;
        VariantLayout layout = isDocument(mimeType) ? VariantLayout.LIBRARY_PAGE : VariantLayout.LIBRARY;
        BufferedImage image = librarySource(mimeType, data, key);
        if (image == null) return;

        if (isDocument(mimeType)) {
            write(scope, category, key, layout.originalName(ImageFormat.WEBP.extension()), image);
        }
        for (int width : config.imageVariantsWidthList()) {
            if (width >= layout.measure(image)) continue;
            try {
                write(scope, category, key, layout.sizeName(width, ImageFormat.WEBP), layout.scale(image, width));
            } catch (IOException e) {
                log.warn("Could not scale a library picture key={} width={}", key, width, e);
            }
        }
    }

    /**
     * The stored file that answers a request: the smallest stored size at or above {@code size} in a
     * format the reader takes, else the original.
     *
     * <p>The library serves its sizes only where the {@code imageVariantsWebp} setting is on, since
     * every one of them is WebP.
     *
     * @param size     the size asked for on the side the profile measures; {@code 0} asks for the
     *                 original
     * @param accepted the formats the reader takes
     * @return the file, or empty when nothing is stored
     */
    public Optional<MediaContent> read(
            ImageProfile profile,
            StorageScope scope,
            StorageCategory category,
            String key,
            int size,
            AcceptedFormats accepted) {
        if (key == null || key.isBlank()) return Optional.empty();
        AcceptedFormats taken = profile == ImageProfile.LIBRARY && !config.imageVariantsWebp()
                ? AcceptedFormats.WITHOUT_WEBP
                : accepted;
        try {
            return set(profile.layout(), scope, category, key)
                    .choose(size, taken)
                    .flatMap(file -> readFile(scope, category, key, file));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * The same, for a reader that takes every format: every browser, and the PDF exports.
     */
    public Optional<MediaContent> read(
            ImageProfile profile, StorageScope scope, StorageCategory category, String key, int size) {
        return read(profile, scope, category, key, size, AcceptedFormats.EVERY_FORMAT);
    }

    /**
     * The picture of a library file, at the width asked for or the nearest kept above it.
     *
     * <p>Unlike {@link #read} this never falls back to a document itself. A picture is asked for by
     * something drawing a tile, and handing a document back under that name puts a whole PDF where an
     * image was expected, which the browser draws as nothing at all. An image with no size large
     * enough is its own picture, a WebP copy of it preferred.
     *
     * @param mimeType the stored type of the file
     * @param size     the width asked for; {@code 0} asks for the full picture
     * @return the picture, or empty where the file has none
     */
    public Optional<MediaContent> picture(
            StorageScope scope, StorageCategory category, String key, String mimeType, int size) {
        if (!canHavePicture(mimeType)) return Optional.empty();
        boolean document = isDocument(mimeType);
        var pictures = set(document ? VariantLayout.LIBRARY_PAGE : VariantLayout.LIBRARY, scope, category, key);
        var chosen = pictures.size(size, AcceptedFormats.EVERY_FORMAT).or(() -> pictures.original(ImageFormat.WEBP));
        if (!document) chosen = chosen.or(pictures::original);
        return chosen.flatMap(file -> readFile(scope, category, key, file));
    }

    /**
     * Whether a picture is stored under {@code (scope, category, key)}.
     */
    public boolean exists(ImageProfile profile, StorageScope scope, StorageCategory category, String key) {
        if (key == null || key.isBlank()) return false;
        try {
            return set(profile.layout(), scope, category, key).original().isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Removes every file stored under {@code (scope, category, key)}. No-op when none exist.
     */
    public void delete(StorageScope scope, StorageCategory category, String key) {
        if (key == null || key.isBlank()) return;
        storage.deletePrefix(scope, category, key);
    }

    /**
     * Decodes an upload whose signature named a picture format. Bytes that carry the signature and
     * cannot be decoded behind it are refused like any other file that is no picture.
     */
    private static BufferedImage decode(byte[] data) {
        try {
            BufferedImage image = PixelBudget.read(data);
            if (image == null) throw Refusal.PICTURE_KIND_NOT_TAKEN.raise();
            return image;
        } catch (IOException e) {
            throw Refusal.PICTURE_KIND_NOT_TAKEN.raise();
        }
    }

    private Encoded original(ImageProfile profile, ImageFormat format, BufferedImage image, byte[] data)
            throws IOException {
        VariantLayout layout = profile.layout();
        String name = layout.originalName(format.extension());
        if (format == ImageFormat.GIF || format == ImageFormat.WEBP) return new Encoded(name, data, format);
        int side = Math.min(layout.measure(image), profile.maxOriginalSide());
        return new Encoded(name, encoder.encode(layout.scale(image, side), format), format);
    }

    private List<Encoded> sizes(ImageProfile profile, ImageFormat source, BufferedImage image) throws IOException {
        if (source == ImageFormat.GIF || !encoder.writes(source)) return List.of();
        VariantLayout layout = profile.layout();
        var encoded = new ArrayList<Encoded>();
        for (int size : profile.sizes()) {
            int side = Math.min(size, layout.measure(image));
            encoded.add(new Encoded(
                    layout.sizeName(size, source), encoder.encode(layout.scale(image, side), source), source));
        }
        return encoded;
    }

    /**
     * The picture a library file's sizes are made from: the image itself, or a document's first page.
     * A GIF keeps its frames by having no sizes, and an image above the pixel budget is never decoded.
     *
     * @return the picture, or null where none can be made
     */
    private static BufferedImage librarySource(String mimeType, byte[] data, String key) {
        try {
            if (isDocument(mimeType)) return FilePicture.firstPage(data, PDF_RENDER_DPI);
            if (ImageFormat.ofMimeType(mimeType).filter(ImageFormat.GIF::equals).isPresent()) return null;
            if (!PixelBudget.fits(data)) {
                log.warn("Skipped the sizes of a library picture above the pixel budget key={}", key);
                return null;
            }
            return PixelBudget.read(data);
        } catch (IOException e) {
            log.warn("Could not read a library picture for its sizes key={}", key, e);
            return null;
        }
    }

    private void write(StorageScope scope, StorageCategory category, String key, String name, BufferedImage image) {
        try {
            byte[] webp = encoder.encode(image, ImageFormat.WEBP);
            storage.store(scope, category, key, new Variant(name), webp, ImageFormat.WEBP.mimeType());
        } catch (IOException e) {
            log.warn("Could not write a library picture key={} name={}", key, name, e);
        }
    }

    private VariantSet set(VariantLayout layout, StorageScope scope, StorageCategory category, String key) {
        return VariantSet.of(layout, storage.listKeys(scope, category, key));
    }

    private Optional<MediaContent> readFile(
            StorageScope scope, StorageCategory category, String key, VariantFile file) {
        Optional<StoredStream> opt = storage.read(scope, category, key, new Variant(file.fileName()));
        if (opt.isEmpty()) return Optional.empty();
        try (StoredStream stream = opt.get()) {
            byte[] bytes = stream.body().readAllBytes();
            return Optional.of(new MediaContent(
                    bytes, MediaTypes.contentTypeOf(file, stream.metadata().contentType())));
        } catch (IOException e) {
            log.warn("Could not read a stored picture scope={} category={} key={}", scope, category, key, e);
            return Optional.empty();
        }
    }

    private record Encoded(String name, byte[] data, ImageFormat format) {}
}
