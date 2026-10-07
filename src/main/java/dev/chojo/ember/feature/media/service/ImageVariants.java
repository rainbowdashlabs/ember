/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.api.refusal.GeneralRefusal;
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
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.jspecify.annotations.Nullable;
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
 * <p>Format rules: the original keeps its format, and every size is written as WebP through
 * {@code cwebp}; a host without {@code cwebp} writes the sizes in the source's format instead, and a
 * WebP original then gets none. A GIF is kept as it came and never resized, since a resize keeps only
 * its first frame. A WebP original is kept as it came as well. A drawing (SVG) is never taken by a
 * sized family and has no picture in the library.
 *
 * <p>No size at or above the picture's own is written: it would be a copy of the original, which is
 * what the chooser answers with when no size is large enough. Sets stored by earlier builds keep their
 * copies and their PNG sizes, and are read by the same names as before.
 */
@Singleton
public class ImageVariants {
    private static final Logger log = LoggerFactory.getLogger(ImageVariants.class);
    private static final String PDF = "application/pdf";
    private static final String SVG = "image/svg+xml";

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
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when the upload is too large, is none of the
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
        ImageFormat format = taken(category, data, maxBytes);
        BufferedImage image = decode(data);

        var encoded = new ArrayList<Encoded>();
        encoded.add(original(profile, format, image, data));
        var sizeFormat = sizeFormat(format);
        if (sizeFormat.isPresent()) {
            encoded.addAll(sizes(profile.layout(), profile.sizes(), image, sizeFormat.get()));
        }

        delete(scope, category, key);
        write(scope, category, key, encoded);
    }

    /**
     * Stores a picture somebody else served as the whole set of a sized family, cut to a square from its
     * middle and at most {@code side} pixels across.
     *
     * <p>Nothing of the bytes that came in is kept. Where {@link #store} keeps a GIF or a WebP as it came,
     * this decodes every picture and writes it again as PNG, so whatever else the file carried stays
     * behind.
     *
     * @param profile  the family, one of the sized ones
     * @param maxBytes upper bound on the size of the bytes that came in; {@code 0} disables the check
     * @param side     the longest the square may be; a smaller picture keeps its own size
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when the bytes are too large, are none of the
     *                                             formats the category takes, cannot be decoded, or
     *                                             declare more pixels than {@link PixelBudget#MAX_PIXELS}
     * @throws IOException                         when the picture cannot be encoded; nothing stored has
     *                                             changed
     */
    public void storeSquare(
            ImageProfile profile,
            StorageScope scope,
            StorageCategory category,
            String key,
            byte[] data,
            int maxBytes,
            int side)
            throws IOException {
        if (profile.layout() != VariantLayout.SIZED) {
            throw new IllegalArgumentException("Only a sized family is cut to a square");
        }
        taken(category, data, maxBytes);
        BufferedImage square = square(decode(data), side);

        var encoded = new ArrayList<Encoded>();
        encoded.add(new Encoded(
                profile.layout().originalName(ImageFormat.PNG.extension()),
                encoder.encode(square, ImageFormat.PNG),
                ImageFormat.PNG));
        var sizeFormat = sizeFormat(ImageFormat.PNG);
        if (sizeFormat.isPresent()) {
            encoded.addAll(sizes(profile.layout(), profile.sizes(), square, sizeFormat.get()));
        }

        delete(scope, category, key);
        write(scope, category, key, encoded);
    }

    /**
     * The format of bytes a sized family is handed, refusing them when they are too large or are no
     * picture the category takes. Only the signature decides, never what the sender declared.
     */
    private static ImageFormat taken(StorageCategory category, byte[] data, int maxBytes) {
        if (maxBytes > 0 && data.length > maxBytes) throw GeneralRefusal.PICTURE_TOO_LARGE.raise();
        return ImageFormat.sniff(data)
                .filter(sniffed -> category.acceptsMimeType(sniffed.mimeType()))
                .orElseThrow(GeneralRefusal.PICTURE_KIND_NOT_TAKEN::raise);
    }

    private static BufferedImage square(BufferedImage image, int side) throws IOException {
        int length = Math.min(side, Math.min(image.getWidth(), image.getHeight()));
        return Thumbnails.of(image).crop(Positions.CENTER).size(length, length).asBufferedImage();
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

        try {
            var encoded = new ArrayList<Encoded>();
            if (isDocument(mimeType)) {
                encoded.add(new Encoded(
                        layout.originalName(ImageFormat.WEBP.extension()),
                        encoder.encode(image, ImageFormat.WEBP),
                        ImageFormat.WEBP));
            }
            encoded.addAll(sizes(layout, config.imageVariantsWidthList(), image, ImageFormat.WEBP));
            write(scope, category, key, encoded);
        } catch (IOException e) {
            log.warn("Could not make the sizes of a library picture key={}", key, e);
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
            if (image == null) throw GeneralRefusal.PICTURE_KIND_NOT_TAKEN.raise();
            return image;
        } catch (IOException e) {
            throw GeneralRefusal.PICTURE_KIND_NOT_TAKEN.raise();
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

    /**
     * The format the sizes of a sized family are written in: WebP wherever {@code cwebp} is installed,
     * else the source's own format where it can be written. A GIF has no sizes.
     */
    private Optional<ImageFormat> sizeFormat(ImageFormat source) {
        if (source == ImageFormat.GIF) return Optional.empty();
        if (encoder.writes(ImageFormat.WEBP)) return Optional.of(ImageFormat.WEBP);
        return encoder.writes(source) ? Optional.of(source) : Optional.empty();
    }

    /**
     * Every size below the picture's own, measured on the side the layout measures. A size at or
     * above it would be a copy of the original, which the chooser falls back to anyway.
     */
    private List<Encoded> sizes(VariantLayout layout, List<Integer> sizes, BufferedImage image, ImageFormat format)
            throws IOException {
        var encoded = new ArrayList<Encoded>();
        for (int size : sizes) {
            if (size >= layout.measure(image)) continue;
            encoded.add(new Encoded(
                    layout.sizeName(size, format), encoder.encode(layout.scale(image, size), format), format));
        }
        return encoded;
    }

    /**
     * The picture a library file's sizes are made from: the image itself, or a document's first page.
     * A GIF keeps its frames by having no sizes, and an image above the pixel budget is never decoded.
     *
     * @return the picture, or null where none can be made
     */
    private static @Nullable BufferedImage librarySource(String mimeType, byte[] data, String key) {
        try {
            if (isDocument(mimeType)) return FilePicture.firstPage(data, FilePicture.PAGE_DPI);
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

    private void write(StorageScope scope, StorageCategory category, String key, List<Encoded> files) {
        for (Encoded file : files) {
            storage.store(
                    scope,
                    category,
                    key,
                    new Variant(file.name()),
                    file.data(),
                    file.format().mimeType());
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
