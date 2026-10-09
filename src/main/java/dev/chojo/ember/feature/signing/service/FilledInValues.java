/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.generator.service.font.BundledFont;
import dev.chojo.ember.feature.generator.service.pdf.FittedText;
import dev.chojo.ember.feature.generator.service.pdf.FontChain;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws what signers typed into the fields of a document they were asked to fill in
 * ({@link FillInField}), before the document is sealed.
 *
 * <p>Each value is drawn onto the page inside the rectangle of its field, in the bundled Liberation Sans
 * embedded as a subset, so a PDF/A document stays PDF/A; it starts at the size a form field's text has and
 * shrinks, onto further lines where it has to, until it fits. A character the font has no glyph for is
 * left out. Drawing onto the page rather than into the field's appearance makes the value show in every
 * viewer and on paper, the same as a signature's mark ({@link SignatureMarks}).
 *
 * <p>Every field of a signature field that was signed is taken out afterwards, filled in or left empty, so
 * nothing invites typing over a signed document. The fields of signers still to come stay, read only: they
 * are filled in by those signers' own acts, each of which seals a fresh version from the frozen content.
 */
final class FilledInValues {
    private static final float SIZE = 10f;
    private static final float ASCENT = 0.9f;

    private FilledInValues() {}

    /**
     * @param document        the document, as the frozen content
     * @param values          what was typed, by the name of the field
     * @param signedSignature the names of the signature fields signed so far
     * @throws IOException when the font cannot be read into the document or a page cannot be drawn on
     */
    static void draw(PDDocument document, Map<String, String> values, Set<String> signedSignature) throws IOException {
        var form = document.getDocumentCatalog().getAcroForm(null);
        if (form == null) return;
        var signed = new ArrayList<PDTextField>();
        FontChain chain = null;
        for (PDField candidate : form.getFieldTree()) {
            if (!(candidate instanceof PDTextField text) || text.getPartialName() == null) continue;
            var signatureField = FillInField.signatureFieldOf(text.getPartialName());
            if (signatureField.isEmpty()) continue;
            if (!signedSignature.contains(signatureField.get())) {
                text.setReadOnly(true);
                continue;
            }
            signed.add(text);
            String value = values.get(text.getPartialName());
            if (value == null || value.isBlank()) continue;
            if (chain == null) chain = new FontChain(List.of(font(document)));
            for (var widget : text.getWidgets()) {
                var page = SignatureMarks.pageOf(document, widget);
                if (page.isPresent()) drawInto(document, page.get(), widget.getRectangle(), value, chain);
            }
        }
        for (var text : signed) {
            SignatureMarks.remove(document, form, text);
        }
    }

    private static PDFont font(PDDocument document) throws IOException {
        return PDType0Font.load(document, new ByteArrayInputStream(BundledFont.data()), true);
    }

    private static void drawInto(PDDocument document, PDPage page, PDRectangle rect, String value, FontChain chain)
            throws IOException {
        float width = rect.getWidth();
        float height = rect.getHeight();
        var fitted = FittedText.fit(chain, value, width, height, SIZE, true);
        float size = fitted.size();
        float step = size * FittedText.LEADING;
        float textHeight = fitted.lines().size() * step;
        float top = rect.getLowerLeftY() + Math.min(height, (height + textHeight) / 2);
        float baseline = top - size * ASCENT;
        try (var stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            stream.saveGraphicsState();
            stream.addRect(rect.getLowerLeftX(), rect.getLowerLeftY(), width, height);
            stream.clip();
            stream.setNonStrokingColor(0f);
            for (var line : fitted.lines()) {
                stream.beginText();
                stream.newLineAtOffset(rect.getLowerLeftX() + FittedText.PADDING, baseline);
                for (var run : line.runs()) {
                    stream.setFont(run.font(), size);
                    stream.showText(run.text());
                }
                stream.endText();
                baseline -= step;
            }
            stream.restoreGraphicsState();
        }
    }
}
