/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.io.IOException;
import java.util.List;

/**
 * Converts presentation files (PPTX, PPT, ODP) to PDF through {@link LibreOffice}.
 */
public final class PresentationConverter {

    private PresentationConverter() {}

    /**
     * Converts a presentation file to PDF.
     *
     * @param data     raw file bytes of the presentation
     * @param filename original filename (used to determine extension)
     * @return the converted PDF bytes
     * @throws IOException if conversion fails
     */
    public static byte[] toPdf(byte[] data, String filename) throws IOException {
        return LibreOffice.toPdf(data, getExtension(filename), null, List.of());
    }

    private static String getExtension(String filename) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot > 0) return filename.substring(dot + 1).toLowerCase();
        }
        return "pptx";
    }
}
