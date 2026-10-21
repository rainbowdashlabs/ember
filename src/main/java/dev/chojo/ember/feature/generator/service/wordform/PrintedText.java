/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.FieldRect;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the letters printed on the pages of a PDF are, each as a box in the page's own coordinates, the
 * way fields are placed. Blank characters are left out, since they print nothing a field could cover.
 */
final class PrintedText extends PDFTextStripper {
    private final List<FieldRect> letters = new ArrayList<>();
    private int page;
    private float pageHeight;

    private PrintedText() {}

    /**
     * @param document an open PDF
     * @return the box of every letter printed on its pages
     * @throws IOException where a page cannot be read
     */
    static List<FieldRect> of(PDDocument document) throws IOException {
        var reader = new PrintedText();
        for (int index = 0; index < document.getNumberOfPages(); index++) {
            reader.page = index + 1;
            reader.pageHeight = document.getPage(index).getCropBox().getHeight();
            reader.setStartPage(index + 1);
            reader.setEndPage(index + 1);
            reader.getText(document);
        }
        return List.copyOf(reader.letters);
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        for (var position : positions) {
            String letter = position.getUnicode();
            if (letter == null || letter.isBlank()) continue;
            double baseline = pageHeight - position.getYDirAdj();
            letters.add(new FieldRect(
                    page, position.getXDirAdj(), baseline, position.getWidthDirAdj(), position.getHeightDir()));
        }
    }
}
