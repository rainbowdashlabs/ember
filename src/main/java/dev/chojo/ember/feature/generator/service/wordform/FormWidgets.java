/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.service.pdf.PdfFiles;
import dev.chojo.ember.feature.generator.service.pdf.PdfInspector;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where LibreOffice put the form field of each place of a Word form, and the PDF without them.
 *
 * <p>Each form field carries the name of its place as its alternate name, the one a reader is shown
 * ({@link FormSpot#tag()}). Once read, every form field and its widget is taken off the PDF: Ember draws
 * its own fields over the page, and a form field left in it would cover them.
 */
final class FormWidgets {

    private FormWidgets() {}

    /**
     * The converted form.
     *
     * @param pdf    the PDF without its form fields
     * @param places where the form field of each place sat, by the name of the place
     * @param pages  the width of each page in points, the first first
     * @param text   the box of every letter the pages print, which a widened field stops short of
     */
    record Taken(byte[] pdf, Map<String, FieldRect> places, double[] pages, List<FieldRect> text) {}

    /**
     * @param pdf the PDF LibreOffice wrote
     * @return where its places are, and the PDF without its form fields
     * @throws IOException where the PDF cannot be read or written
     */
    static Taken take(byte[] pdf) throws IOException {
        try (PDDocument document = PdfFiles.open(pdf)) {
            var places = new HashMap<String, FieldRect>();
            var form = document.getDocumentCatalog().getAcroForm(null);
            if (form != null) {
                for (PDField field : form.getFieldTree()) {
                    String name = field.getAlternateFieldName();
                    if (name == null || field.getWidgets().isEmpty()) continue;
                    PDAnnotationWidget widget = field.getWidgets().getFirst();
                    var page = PdfInspector.pageOf(document, widget);
                    var rect = widget.getRectangle();
                    if (page.isEmpty() || rect == null) continue;
                    places.putIfAbsent(
                            name.strip(),
                            new FieldRect(
                                    page.get(),
                                    rect.getLowerLeftX(),
                                    rect.getLowerLeftY(),
                                    rect.getWidth(),
                                    rect.getHeight()));
                }
                document.getDocumentCatalog().setAcroForm(null);
            }
            double[] widths = new double[document.getNumberOfPages()];
            for (int index = 0; index < widths.length; index++) {
                var page = document.getPage(index);
                widths[index] = page.getCropBox().getWidth();
                var kept = new ArrayList<PDAnnotation>();
                for (var annotation : page.getAnnotations()) {
                    if (!(annotation instanceof PDAnnotationWidget)) kept.add(annotation);
                }
                page.setAnnotations(kept);
            }
            return new Taken(PdfFiles.save(document), Map.copyOf(places), widths, PrintedText.of(document));
        }
    }
}
