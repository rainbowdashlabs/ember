/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FormField;
import dev.chojo.ember.feature.generator.entity.FormFieldKind;
import dev.chojo.ember.feature.generator.entity.PdfInspection;
import dev.chojo.ember.feature.generator.entity.PdfPage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads what fields care about out of a PDF: the shown part and the turn of every page, and the form
 * fields it brings.
 */
public final class PdfInspector {

    private PdfInspector() {}

    /**
     * @param document an open PDF
     * @return its pages and form fields
     */
    public static PdfInspection inspect(PDDocument document) {
        var pages = new ArrayList<PdfPage>();
        for (PDPage page : document.getPages()) {
            var crop = page.getCropBox();
            pages.add(new PdfPage(
                    crop.getLowerLeftX(), crop.getLowerLeftY(), crop.getWidth(), crop.getHeight(), rotationOf(page)));
        }
        return new PdfInspection(pages, formFields(document));
    }

    /**
     * How far a page is turned when shown, as one of the four quarter turns.
     *
     * @param page the page
     * @return 0, 90, 180 or 270
     */
    public static int rotationOf(PDPage page) {
        int turned = Math.floorMod(page.getRotation(), 360);
        return Math.floorMod(Math.round(turned / 90f) * 90, 360);
    }

    /**
     * The page a widget sits on, counted from one, asked of the widget first and of the pages' lists of
     * annotations where the widget does not say.
     *
     * @param document the document
     * @param widget   the widget
     * @return the page, or empty where it is on none
     */
    public static Optional<Integer> pageOf(PDDocument document, PDAnnotationWidget widget) {
        var pages = document.getPages();
        PDPage named = widget.getPage();
        if (named != null) {
            int index = pages.indexOf(named);
            if (index >= 0) return Optional.of(index + 1);
        }
        for (int index = 0; index < pages.getCount(); index++) {
            try {
                for (var annotation : pages.get(index).getAnnotations()) {
                    if (annotation.getCOSObject() == widget.getCOSObject()) return Optional.of(index + 1);
                }
            } catch (IOException unreadable) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static List<FormField> formFields(PDDocument document) {
        var form = document.getDocumentCatalog().getAcroForm(null);
        if (form == null) return List.of();
        var fields = new ArrayList<FormField>();
        for (PDField field : form.getFieldTree()) {
            if (!(field instanceof PDTerminalField terminal)) continue;
            fields.add(new FormField(
                    field.getFullyQualifiedName(),
                    kindOf(field),
                    firstRect(document, terminal),
                    blankAsNull(field.getAlternateFieldName()),
                    field instanceof PDTextField ? blankAsNull(field.getValueAsString()) : null));
        }
        return fields;
    }

    private static @Nullable String blankAsNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static FormFieldKind kindOf(PDField field) {
        if (field instanceof PDTextField) return FormFieldKind.TEXT;
        if (field instanceof PDCheckBox) return FormFieldKind.CHECK;
        if (field instanceof PDSignatureField) return FormFieldKind.SIGNATURE;
        return FormFieldKind.OTHER;
    }

    private static @Nullable FieldRect firstRect(PDDocument document, PDTerminalField field) {
        for (var widget : field.getWidgets()) {
            var rect = widget.getRectangle();
            var page = pageOf(document, widget);
            if (rect == null || page.isEmpty()) continue;
            return new FieldRect(
                    page.get(), rect.getLowerLeftX(), rect.getLowerLeftY(), rect.getWidth(), rect.getHeight());
        }
        return null;
    }
}
