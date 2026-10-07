/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FormBinding;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Fills the form fields a PDF brings and flattens all of them into the page.
 *
 * <p>A check box bound to a text is ticked through its own appearance, so it looks the way the form
 * draws it. A text field is emptied instead and its text is handed back to be drawn like any field
 * drawn on the page: the font the form names may lack a glyph, and only the stamper falls back to
 * another font character by character. The text keeps the field's size, its alignment and whether it
 * runs onto several lines.
 *
 * <p>Every form field is flattened, bound or not, so the filled document is a plain page that nobody
 * changes by typing into it. The signature fields of the template are added afterwards and stay
 * fields.
 */
final class FormFiller {
    /** The size a field without one of its own starts at before it shrinks to fit. */
    private static final float AUTO_SIZE = 12f;

    private static final Pattern FONT_SIZE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s+Tf");

    private FormFiller() {}

    /**
     * A text a form field showed, to be drawn into its widget.
     *
     * @param rect  where the widget sits
     * @param text  the text, filled in
     * @param size  the size the field asks for
     * @param align where the text sits across it
     * @param wrap  whether the field takes several lines
     */
    record FormText(FieldRect rect, String text, float size, TextAlign align, boolean wrap) {}

    /**
     * Fills and flattens the form of a document.
     *
     * @param document the document
     * @param bindings what the form fields are filled with
     * @param fill     fills the placeholders of a text
     * @return the texts of the bound text fields, to be drawn where their widgets were
     * @throws IOException where the form cannot be read or flattened
     */
    static List<FormText> fill(PDDocument document, List<FormBinding> bindings, UnaryOperator<String> fill)
            throws IOException {
        var form = document.getDocumentCatalog().getAcroForm(null);
        if (form == null) return List.of();
        form.setXFA(null);
        var texts = new ArrayList<FormText>();
        for (var binding : bindings) {
            var field = form.getField(binding.fieldName());
            String filled = fill.apply(binding.text());
            if (field instanceof PDTextField text) {
                texts.addAll(textsOf(document, text, filled));
                empty(text);
            } else if (field instanceof PDCheckBox box) {
                if (YesWords.saysYes(filled)) {
                    box.check();
                } else {
                    box.unCheck();
                }
            }
        }
        form.setNeedAppearances(false);
        var all = new ArrayList<PDField>();
        form.getFieldTree().forEach(all::add);
        form.flatten(all, false);
        return texts;
    }

    private static List<FormText> textsOf(PDDocument document, PDTextField field, String text) {
        var texts = new ArrayList<FormText>();
        for (var widget : field.getWidgets()) {
            var rect = widget.getRectangle();
            var page = PdfInspector.pageOf(document, widget);
            if (rect == null || page.isEmpty()) continue;
            texts.add(new FormText(
                    new FieldRect(
                            page.get(), rect.getLowerLeftX(), rect.getLowerLeftY(), rect.getWidth(), rect.getHeight()),
                    text,
                    sizeOf(field),
                    alignOf(field),
                    field.isMultiline()));
        }
        return texts;
    }

    /**
     * Empties a text field so its own appearance shows its frame and nothing in it. Where the form
     * cannot draw it empty, the appearance goes and the field shows nothing at all.
     */
    private static void empty(PDTextField field) {
        try {
            field.setValue("");
        } catch (IOException | IllegalArgumentException | IllegalStateException cannotDraw) {
            field.getWidgets().forEach(widget -> widget.getCOSObject().removeItem(COSName.AP));
        }
    }

    private static float sizeOf(PDTextField field) {
        String appearance = field.getDefaultAppearance();
        if (appearance == null) return AUTO_SIZE;
        var matcher = FONT_SIZE.matcher(appearance);
        if (!matcher.find()) return AUTO_SIZE;
        float size = Float.parseFloat(matcher.group(1));
        return size > 0 ? size : AUTO_SIZE;
    }

    private static TextAlign alignOf(PDTextField field) {
        return switch (field.getQ()) {
            case 1 -> TextAlign.CENTER;
            case 2 -> TextAlign.RIGHT;
            default -> TextAlign.LEFT;
        };
    }
}
