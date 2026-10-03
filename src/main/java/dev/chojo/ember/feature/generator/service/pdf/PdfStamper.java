/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfLayout;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * Fills an uploaded PDF in place: the texts and crosses of its fields drawn onto its pages, its own
 * form fields filled and flattened, and an empty signature field for every signer.
 *
 * <p>The original is opened and never written back; what comes out is a new file. Everything drawn is
 * appended to the page's content with the page's earlier state wrapped and restored first, so whatever
 * the page left behind (a moved origin, a colour, a clip) cannot shift or hide a field. Text is
 * embedded as a font subset, and a text runs upright on a turned page. Nothing else in the file
 * changes, so a PDF/A comes out as a PDF/A; a PDF that was none is not made one.
 *
 * <p>A field on a page the PDF does not have, as after a new upload with fewer pages, is left out.
 */
@Singleton
public class PdfStamper {
    private static final float LINE_WIDTH = 0.6f;

    private final StampFonts fonts;

    @Inject
    public PdfStamper(StampFonts fonts) {
        this.fonts = fonts;
    }

    /**
     * A filled-in PDF.
     *
     * @param pdf         the file
     * @param unprintable the characters of the filled-in texts that no font could print, left out
     */
    public record Stamped(byte[] pdf, List<String> unprintable) {}

    /**
     * Fills a PDF.
     *
     * @param original the PDF as it was uploaded
     * @param layout   the fields and the form fields' values
     * @param fill     fills the placeholders of a text
     * @return the filled-in PDF
     * @throws IOException where the PDF cannot be read or written
     */
    public Stamped stamp(byte[] original, PdfLayout layout, UnaryOperator<String> fill) throws IOException {
        try (var document = PdfFiles.open(original)) {
            var chain = fonts.chainFor(document);
            var unprintable = new LinkedHashSet<String>();
            var byPage = new TreeMap<Integer, List<Placed>>();
            for (var text : FormFiller.fill(document, layout.bindings(), fill)) {
                place(byPage, text.rect(), new Mark.Text(text.text(), text.size(), text.align(), text.wrap()));
            }
            for (var field : layout.fields()) {
                place(byPage, field.rect(), markOf(field, fill));
            }
            for (var page : byPage.entrySet()) {
                if (page.getKey() > document.getNumberOfPages()) continue;
                draw(document, document.getPage(page.getKey() - 1), page.getValue(), chain, unprintable);
            }
            for (var field : layout.fields()) {
                var role = field.role();
                if (role != null) addSignatureField(document, field.rect(), role);
            }
            return new Stamped(PdfFiles.save(document), List.copyOf(unprintable));
        }
    }

    /** What is drawn into one box. */
    private sealed interface Mark {
        /** A text, laid out to fit. */
        record Text(String text, float size, TextAlign align, boolean wrap) implements Mark {}

        /** A cross through the box. */
        record Cross() implements Mark {}

        /** A line along the bottom of the box, to sign on. */
        record Line() implements Mark {}

        /** Nothing, for a check field that says no. */
        record Nothing() implements Mark {}
    }

    /** A mark with its box. */
    private record Placed(FieldRect rect, Mark mark) {}

    private static Mark markOf(PdfField field, UnaryOperator<String> fill) {
        String text = fill.apply(Objects.requireNonNullElse(field.text(), ""));
        return switch (field.kind()) {
            case TEXT -> new Mark.Text(text, (float) field.fontSize(), field.align(), field.wrap());
            case CHECK -> YesWords.saysYes(text) ? new Mark.Cross() : new Mark.Nothing();
            case SIGNATURE -> new Mark.Line();
        };
    }

    private static void place(TreeMap<Integer, List<Placed>> byPage, FieldRect rect, Mark mark) {
        byPage.computeIfAbsent(rect.page(), page -> new ArrayList<>()).add(new Placed(rect, mark));
    }

    private static void draw(
            PDDocument document, PDPage page, List<Placed> marks, FontChain chain, Set<String> unprintable)
            throws IOException {
        int rotation = PdfInspector.rotationOf(page);
        try (var content = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
            for (var placed : marks) {
                var frame = FieldFrame.of(placed.rect(), rotation);
                content.saveGraphicsState();
                content.transform(frame.toPage());
                switch (placed.mark()) {
                    case Mark.Text text -> drawText(content, frame, text, chain, unprintable);
                    case Mark.Cross ignored -> drawCross(content, frame);
                    case Mark.Line ignored -> drawLine(content, frame);
                    case Mark.Nothing ignored -> {}
                }
                content.restoreGraphicsState();
            }
        }
    }

    private static void drawText(
            PDPageContentStream content, FieldFrame frame, Mark.Text text, FontChain chain, Set<String> unprintable)
            throws IOException {
        if (text.text().isBlank()) return;
        var fitted = FittedText.fit(chain, text.text(), frame.width(), frame.height(), text.size(), text.wrap());
        unprintable.addAll(fitted.unprintable());
        content.addRect(0, 0, frame.width(), frame.height());
        content.clip();
        content.setNonStrokingColor(0f);
        float size = fitted.size();
        float step = size * FittedText.LEADING;
        float baseline = text.wrap()
                ? frame.height() - FittedText.PADDING - ascentOf(chain.primary()) * size
                : (frame.height() - capHeightOf(chain.primary()) * size) / 2;
        for (var line : fitted.lines()) {
            float x =
                    switch (text.align()) {
                        case LEFT -> FittedText.PADDING;
                        case CENTER -> (frame.width() - line.width(size)) / 2;
                        case RIGHT -> frame.width() - FittedText.PADDING - line.width(size);
                    };
            content.beginText();
            content.newLineAtOffset(x, baseline);
            for (var run : line.runs()) {
                content.setFont(run.font(), size);
                content.showText(run.text());
            }
            content.endText();
            baseline -= step;
        }
    }

    private static void drawCross(PDPageContentStream content, FieldFrame frame) throws IOException {
        float side = Math.min(frame.width(), frame.height());
        float left = (frame.width() - side) / 2 + side * 0.2f;
        float bottom = (frame.height() - side) / 2 + side * 0.2f;
        float reach = side * 0.6f;
        content.setStrokingColor(0f);
        content.setLineWidth(Math.max(LINE_WIDTH, side * 0.1f));
        content.moveTo(left, bottom);
        content.lineTo(left + reach, bottom + reach);
        content.moveTo(left, bottom + reach);
        content.lineTo(left + reach, bottom);
        content.stroke();
    }

    private static void drawLine(PDPageContentStream content, FieldFrame frame) throws IOException {
        content.setStrokingColor(0f);
        content.setLineWidth(LINE_WIDTH);
        content.moveTo(0, LINE_WIDTH / 2);
        content.lineTo(frame.width(), LINE_WIDTH / 2);
        content.stroke();
    }

    private static float ascentOf(PDFont font) {
        var descriptor = font.getFontDescriptor();
        float ascent = descriptor == null ? 0 : descriptor.getAscent() / 1000f;
        return ascent > 0.5f && ascent < 1.2f ? ascent : 0.9f;
    }

    private static float capHeightOf(PDFont font) {
        var descriptor = font.getFontDescriptor();
        float capHeight = descriptor == null ? 0 : descriptor.getCapHeight() / 1000f;
        return capHeight > 0.4f && capHeight < 1f ? capHeight : 0.7f;
    }

    private static void addSignatureField(PDDocument document, FieldRect rect, SignatureRole role) throws IOException {
        if (rect.page() > document.getNumberOfPages()) return;
        PDPage page = document.getPage(rect.page() - 1);
        var box = new PDRectangle((float) rect.x(), (float) rect.y(), (float) rect.width(), (float) rect.height());
        SignatureFields.add(document, page, box, role);
    }
}
