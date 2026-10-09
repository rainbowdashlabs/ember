/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.PdfLayout;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.SignerCaptions;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.jspecify.annotations.Nullable;

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
 * form fields filled and flattened, an empty signature field for every signer the member has with the
 * signer's name printed under it ({@link SignerCaptions}), and an empty
 * text field ({@link FillInFields}) for every field such a signer is asked to fill in.
 *
 * <p>The original is opened and never written back; what comes out is a new file. Everything drawn is
 * appended to the page's content with the page's earlier state wrapped and restored first, so whatever
 * the page left behind (a moved origin, a colour, a clip) cannot shift or hide a field. Text is
 * embedded as a font subset, in the family of uploaded fonts its field names, or the instance's default
 * font where it names none, with Liberation Sans for every character that font lacks, and a text runs
 * upright on a turned page. Nothing else in the file
 * changes, so a PDF/A comes out as a PDF/A; a PDF that was none is not made one.
 *
 * <p>A field on a page the PDF does not have, as after a new upload with fewer pages, is left out.
 */
@Singleton
public class PdfStamper {
    private static final float LINE_WIDTH = 0.6f;

    /** The gap in points between the fields a signature box is shared out into. */
    private static final double SIGNATURE_GAP = 12;

    /** The height a line of a signature field's text takes, as a multiple of its size. */
    private static final double SIGNATURE_TEXT_LEADING = 1.3;

    /** The size of the signer's name under a signature field, as a share of the field's text size. */
    private static final double SIGNER_SCALE = 0.85;

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
     * <p>A signature field asks for as many fields as its signer has people to sign for the member
     * ({@link SignatureRole#fieldNames}): where that is several, the box is shared out side by side, and
     * where it is none, as for a second guardian the member does not have, the box stays as the PDF has
     * it.
     *
     * @param original  the PDF as it was uploaded
     * @param layout    the fields and the form fields' values
     * @param guardians how many guardians the member has
     * @param fill      fills the placeholders of a text
     * @param signers   the names printed under the signature fields
     * @return the filled-in PDF
     * @throws IOException where the PDF cannot be read or written
     */
    public Stamped stamp(
            byte[] original, PdfLayout layout, int guardians, UnaryOperator<String> fill, SignerCaptions signers)
            throws IOException {
        return stamp(original, layout, guardians, fill, signers, StampFonts.FieldFonts.NONE);
    }

    /**
     * Fills a PDF whose text fields may print in families of uploaded fonts.
     *
     * @param original   the PDF as it was uploaded
     * @param layout     the fields and the form fields' values
     * @param guardians  how many guardians the member has
     * @param fill       fills the placeholders of a text
     * @param signers    the names printed under the signature fields
     * @param fieldFonts where the file of a family a field names comes from
     * @return the filled-in PDF
     * @throws IOException where the PDF cannot be read or written
     */
    public Stamped stamp(
            byte[] original,
            PdfLayout layout,
            int guardians,
            UnaryOperator<String> fill,
            SignerCaptions signers,
            StampFonts.FieldFonts fieldFonts)
            throws IOException {
        try (var document = PdfFiles.open(original)) {
            var loaded = fonts.load(document, fieldFonts);
            var unprintable = new LinkedHashSet<String>();
            var byPage = new TreeMap<Integer, List<Placed>>();
            var signatures = new ArrayList<SignatureBox>();
            for (var text : FormFiller.fill(document, layout.bindings(), fill)) {
                place(
                        byPage,
                        text.rect(),
                        new Mark.Text(text.text(), text.size(), text.align(), text.wrap(), null, FontStyle.REGULAR));
            }
            var fillIns = new ArrayList<FillInBox>();
            var fields = layout.fields();
            for (int position = 0; position < fields.size(); position++) {
                var field = fields.get(position);
                var role = field.role();
                if (role == null) {
                    place(byPage, field.rect(), markOf(field, fill));
                    continue;
                }
                if (field.kind() == PdfFieldKind.FILL_IN) {
                    fillIns.addAll(fillInBoxes(field, position, role.fieldNames(guardians)));
                    continue;
                }
                for (var box : signatureBoxes(field.rect(), role.fieldNames(guardians))) {
                    signatures.add(placeSignature(byPage, field, box, signers.of(box.name()), fill));
                }
            }
            for (var page : byPage.entrySet()) {
                if (page.getKey() > document.getNumberOfPages()) continue;
                draw(document, document.getPage(page.getKey() - 1), page.getValue(), loaded, unprintable);
            }
            for (var box : signatures) {
                addSignatureField(document, box);
            }
            for (var box : fillIns) {
                addFillInField(document, box);
            }
            return new Stamped(PdfFiles.save(document), List.copyOf(unprintable));
        }
    }

    /** One signature field with the part of its box it takes. */
    private record SignatureBox(FieldRect rect, String name) {}

    /** One field to fill in with the part of its box it takes. */
    private record FillInBox(FieldRect rect, FillInField field) {}

    /**
     * The fields to fill in that one field of the template becomes: one for each signature field of its
     * signer, sharing the box out the way a signature field does, each named after its signature field and
     * the template field's position.
     */
    private static List<FillInBox> fillInBoxes(PdfField field, int position, List<String> signatureFields) {
        String label = Objects.requireNonNullElse(field.text(), "");
        int maxLength = FillInField.effectiveMaxLength(field.maxLength());
        return signatureBoxes(field.rect(), signatureFields).stream()
                .map(box -> new FillInBox(
                        box.rect(),
                        new FillInField(
                                FillInField.nameOf(box.name(), position),
                                box.name(),
                                label,
                                field.required(),
                                maxLength)))
                .toList();
    }

    /**
     * What one signer's part of a signature field prints: along the bottom the name of whoever signs there,
     * small and in the field's family, and below it the field's text where it is to print; above them the
     * line to sign on, which a PDF that already has a line leaves out. The name is left out where the text
     * already prints it. The texts take at most half of the part, so a box drawn too low still leaves room
     * to sign.
     *
     * @return the signature field, which takes the part above the texts, so a signature drawn into it later
     *         never covers them
     */
    private static SignatureBox placeSignature(
            TreeMap<Integer, List<Placed>> byPage,
            PdfField field,
            SignatureBox box,
            String signer,
            UnaryOperator<String> fill) {
        var rect = box.rect();
        String text = field.printText() && field.text() != null ? fill.apply(field.text()) : null;
        var captions = new ArrayList<Mark.Text>();
        if (text == null || !text.contains(signer)) {
            captions.add(new Mark.Text(
                    signer,
                    (float) (field.fontSize() * SIGNER_SCALE),
                    field.align(),
                    false,
                    field.fontFamily(),
                    FontStyle.REGULAR));
        }
        if (text != null) {
            captions.add(new Mark.Text(text, (float) field.fontSize(), field.align(), false, null, FontStyle.REGULAR));
        }
        double wanted = captions.stream()
                .mapToDouble(caption -> caption.size() * SIGNATURE_TEXT_LEADING)
                .sum();
        double scale = Math.min(1, rect.height() / 2 / wanted);
        double top = rect.y() + wanted * scale;
        double next = top;
        for (var caption : captions) {
            double height = caption.size() * SIGNATURE_TEXT_LEADING * scale;
            next -= height;
            place(byPage, new FieldRect(rect.page(), rect.x(), next, rect.width(), height), caption);
        }
        var lineRect = new FieldRect(rect.page(), rect.x(), top, rect.width(), rect.height() - (top - rect.y()));
        if (!field.withoutLine()) place(byPage, lineRect, new Mark.Line());
        return new SignatureBox(lineRect, box.name());
    }

    /**
     * Shares a signature box out among the fields it holds, side by side with a small gap between them.
     */
    private static List<SignatureBox> signatureBoxes(FieldRect rect, List<String> names) {
        int count = names.size();
        double gap = count > 1 ? Math.min(SIGNATURE_GAP, rect.width() / (count * 4)) : 0;
        double width = (rect.width() - gap * (count - 1)) / Math.max(1, count);
        var boxes = new ArrayList<SignatureBox>();
        for (int index = 0; index < count; index++) {
            var part = new FieldRect(rect.page(), rect.x() + index * (width + gap), rect.y(), width, rect.height());
            boxes.add(new SignatureBox(part, names.get(index)));
        }
        return boxes;
    }

    /** What is drawn into one box. */
    private sealed interface Mark {
        /** A text, laid out to fit, in a family of uploaded fonts or, where it names none, the default font. */
        record Text(
                String text,
                float size,
                TextAlign align,
                boolean wrap,
                @Nullable String family,
                FontStyle style) implements Mark {}

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
            case TEXT ->
                new Mark.Text(
                        text,
                        (float) field.fontSize(),
                        field.align(),
                        field.wrap(),
                        field.fontFamily(),
                        Objects.requireNonNullElse(field.fontStyle(), FontStyle.REGULAR));
            case CHECK -> YesWords.saysYes(text) ? new Mark.Cross() : new Mark.Nothing();
            case SIGNATURE -> new Mark.Line();
            case FILL_IN -> new Mark.Nothing();
        };
    }

    private static void place(TreeMap<Integer, List<Placed>> byPage, FieldRect rect, Mark mark) {
        byPage.computeIfAbsent(rect.page(), page -> new ArrayList<>()).add(new Placed(rect, mark));
    }

    private static void draw(
            PDDocument document, PDPage page, List<Placed> marks, StampFonts.Loaded fonts, Set<String> unprintable)
            throws IOException {
        int rotation = PdfInspector.rotationOf(page);
        try (var content = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
            for (var placed : marks) {
                var frame = FieldFrame.of(placed.rect(), rotation);
                content.saveGraphicsState();
                content.transform(frame.toPage());
                switch (placed.mark()) {
                    case Mark.Text text ->
                        drawText(content, frame, text, fonts.chain(text.family(), text.style()), unprintable);
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

    private static void addSignatureField(PDDocument document, SignatureBox box) throws IOException {
        var rect = box.rect();
        if (rect.page() > document.getNumberOfPages()) return;
        PDPage page = document.getPage(rect.page() - 1);
        var area = new PDRectangle((float) rect.x(), (float) rect.y(), (float) rect.width(), (float) rect.height());
        SignatureFields.add(document, page, area, box.name());
    }

    private static void addFillInField(PDDocument document, FillInBox box) throws IOException {
        var rect = box.rect();
        if (rect.page() > document.getNumberOfPages()) return;
        PDPage page = document.getPage(rect.page() - 1);
        var area = new PDRectangle((float) rect.x(), (float) rect.y(), (float) rect.width(), (float) rect.height());
        FillInFields.add(document, page, area, box.field());
    }
}
