/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import java.util.List;
import java.util.Locale;

/**
 * What a file actually is, read from its first bytes rather than from what it is called.
 *
 * <p>A name and a declared type are both written by whoever sent the mail, so neither can be trusted
 * to say what is in the file. Reading the bytes is the cheapest guard there is against an executable
 * arriving as {@code invoice.pdf}, and it costs a comparison of a dozen bytes.
 *
 * <p>An unrecognised file is not an error and not a type: it comes back as nothing, and the caller
 * refuses it because no rule can accept a type that could not be established. That is the right way
 * round for a store that keeps other people's paperwork.
 */
public final class ContentSniffer {
    private static final String PDF = "application/pdf";
    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG_MAGIC = {
        (byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
    };
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** The types a rule may be set to accept, which is what the page offers and what this can answer. */
    public static final List<String> SUPPORTED_TYPES = List.of(PDF, PNG, JPEG);

    private ContentSniffer() {}

    /**
     * The type of these bytes, or null where they are none this recognises.
     *
     * @param data the file as it arrived
     * @return the content type, or null where the bytes say nothing this knows
     */
    public static String sniff(byte[] data) {
        if (data == null) return null;
        if (startsWith(data, PDF_MAGIC)) return PDF;
        if (startsWith(data, PNG_MAGIC)) return PNG;
        if (startsWith(data, JPEG_MAGIC)) return JPEG;
        return null;
    }

    /**
     * Whether the name a file arrived under agrees with what the bytes turned out to be.
     *
     * <p>A disagreement is refused rather than corrected. The bytes are the authority on what the file
     * is, but a file whose name says something else was either produced by something broken or sent by
     * somebody hoping the name would be believed, and neither belongs in a document store.
     *
     * <p>A name carrying no extension at all agrees with everything: there is nothing to contradict.
     *
     * @param fileName the name the file arrived under
     * @param sniffed  what the bytes turned out to be
     * @return whether the two agree
     */
    public static boolean nameAgrees(String fileName, String sniffed) {
        if (sniffed == null) return false;
        if (fileName == null || fileName.isBlank()) return true;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return true;
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> PDF.equals(sniffed);
            case "png" -> PNG.equals(sniffed);
            case "jpg", "jpeg" -> JPEG.equals(sniffed);
            default -> true;
        };
    }

    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }
}
