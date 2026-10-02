/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import java.util.Locale;

/**
 * The one table between a stored file's extension and the type it is served as.
 *
 * <p>Beside the four picture formats the library keeps drawings and PDFs, and anything else is a
 * plain stream of bytes stored under {@code .bin}.
 */
public final class MediaTypes {
    /** What a file of no known kind is served as. */
    public static final String UNTYPED = "application/octet-stream";

    private static final String SVG = "image/svg+xml";
    private static final String PDF = "application/pdf";

    private MediaTypes() {}

    /**
     * The extension a file of this type is stored under.
     *
     * @param mimeType the declared or sniffed type, possibly {@code null}
     * @return the extension without its dot, {@code bin} for anything unknown
     */
    public static String extensionFor(String mimeType) {
        var picture = ImageFormat.ofMimeType(mimeType);
        if (picture.isPresent()) return picture.get().extension();
        if (mimeType == null) return "bin";
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case SVG -> "svg";
            case PDF -> "pdf";
            default -> "bin";
        };
    }

    /**
     * The type a file stored under this extension is served as.
     *
     * @param extension the extension without its dot, possibly {@code null}
     * @return the type, {@link #UNTYPED} for anything unknown
     */
    public static String mimeTypeFor(String extension) {
        var picture = ImageFormat.ofExtension(extension);
        if (picture.isPresent()) return picture.get().mimeType();
        if (extension == null) return UNTYPED;
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "svg" -> SVG;
            case "pdf" -> PDF;
            default -> UNTYPED;
        };
    }

    /**
     * The type a stored file is served as: what its sidecar recorded, unless that says nothing, in
     * which case its extension decides. A WebP copy is always served as WebP, whatever the sidecar of
     * an older build wrote beside it.
     *
     * @param file       the stored file
     * @param storedType the type its sidecar recorded, possibly {@code null}
     * @return the type to serve it as
     */
    public static String contentTypeOf(VariantFile file, String storedType) {
        if (file.format().filter(ImageFormat.WEBP::equals).isPresent()) return ImageFormat.WEBP.mimeType();
        if (storedType != null && !storedType.isBlank() && !UNTYPED.equals(storedType)) return storedType;
        return mimeTypeFor(file.extension());
    }
}
