/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;

import java.util.Locale;
import java.util.Set;

/**
 * Decides how an upload is treated in the knowledge base. The MIME type the browser reported has
 * the final say; uploads that arrive without one, or with a generic one, fall back to the file
 * name extension so a recognisable document is not stored as an opaque blob.
 */
public final class KbFileTypeDetector {
    private static final Set<String> PRESENTATION_MIME_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/vnd.oasis.opendocument.presentation");
    private static final Set<String> PRESENTATION_EXTENSIONS = Set.of(".pptx", ".ppt", ".odp");
    private static final Set<String> MARKDOWN_EXTENSIONS = Set.of(".md", ".markdown");
    private static final Set<String> PDF_EXTENSIONS = Set.of(".pdf");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(".txt");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg");

    private KbFileTypeDetector() {}

    /**
     * Determines the knowledge-base file type of an upload.
     *
     * @param mimeType the MIME type reported for the upload, possibly {@code null}
     * @param filename the uploaded file name, possibly {@code null}
     * @return the detected type, {@link KbFileType#OTHER} when nothing matches
     */
    public static KbFileType detect(String mimeType, String filename) {
        if (mimeType != null) {
            if (PRESENTATION_MIME_TYPES.contains(mimeType)) return KbFileType.PRESENTATION;
            if (mimeType.equals("application/pdf")) return KbFileType.PDF;
            if (mimeType.startsWith("image/")) return KbFileType.IMAGE;
            if (mimeType.equals("text/markdown") || endsWithAny(filename, MARKDOWN_EXTENSIONS))
                return KbFileType.MARKDOWN;
            if (mimeType.startsWith("text/")) return KbFileType.TEXT;
        }
        if (filename != null) {
            if (endsWithAny(filename, PRESENTATION_EXTENSIONS)) return KbFileType.PRESENTATION;
            if (endsWithAny(filename, PDF_EXTENSIONS)) return KbFileType.PDF;
            if (endsWithAny(filename, MARKDOWN_EXTENSIONS)) return KbFileType.MARKDOWN;
            if (endsWithAny(filename, TEXT_EXTENSIONS)) return KbFileType.TEXT;
            if (endsWithAny(filename, IMAGE_EXTENSIONS)) return KbFileType.IMAGE;
        }
        return KbFileType.OTHER;
    }

    private static boolean endsWithAny(String filename, Set<String> extensions) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(lower::endsWith);
    }
}
