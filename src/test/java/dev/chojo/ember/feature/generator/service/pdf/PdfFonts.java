/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

/** What a PDF a test produced embeds: the fonts its pages draw in, and whether it claims to be a PDF/A. */
public final class PdfFonts {
    private PdfFonts() {}

    /**
     * @param pdf a PDF
     * @return the names of every font its pages and their forms use, a subset's tag included
     * @throws IOException where it cannot be read
     */
    public static Set<String> namesIn(byte[] pdf) throws IOException {
        var names = new TreeSet<String>();
        try (var document = Loader.loadPDF(pdf)) {
            for (var page : document.getPages()) collect(page.getResources(), names);
        }
        return names;
    }

    /**
     * @param pdf a PDF
     * @return whether its metadata names a PDF/A part
     * @throws IOException where it cannot be read
     */
    public static boolean isPdfA(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            var metadata = document.getDocumentCatalog().getMetadata();
            if (metadata == null) return false;
            try (var in = metadata.exportXMPMetadata()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("pdfaid:part");
            }
        }
    }

    private static void collect(PDResources resources, Set<String> names) throws IOException {
        if (resources == null) return;
        for (var name : resources.getFontNames()) {
            var font = resources.getFont(name);
            if (font != null) names.add(font.getName());
        }
        for (var name : resources.getXObjectNames()) {
            if (resources.getXObject(name) instanceof PDFormXObject form) collect(form.getResources(), names);
        }
    }
}
