/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.FormBinding;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.PdfLayout;
import dev.chojo.ember.feature.generator.entity.PlaceholderTokens;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import dev.chojo.ember.feature.generator.service.font.DefaultFont;
import dev.chojo.ember.feature.generator.service.font.TestFonts;
import dev.chojo.ember.util.PdfText;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filling an uploaded PDF: where a text lands on plain, cropped and turned pages, the fallback for a
 * glyph the font lacks, the default font, check marks, the PDF's own form filled and flattened, and the
 * empty signature fields by role. Every result is read back from the file.
 */
class PdfStamperTest {
    private static final Map<String, String> VALUES = Map.of(
            "member.fullName", "Lena Schmidt",
            "yes", "Ja",
            "no", "Nein");
    private static final UnaryOperator<String> FILL = text -> PlaceholderTokens.fill(text, VALUES);

    private final PdfStamper stamper = new PdfStamper(new StampFonts(DefaultFont.absent()));

    private static PdfField text(FieldRect rect, String text, TextAlign align, boolean wrap) {
        return new PdfField(PdfFieldKind.TEXT, rect, text, 12, align, wrap, null);
    }

    private static PdfField check(FieldRect rect, String text) {
        return new PdfField(PdfFieldKind.CHECK, rect, text, 10, TextAlign.LEFT, false, null);
    }

    private static PdfField signature(FieldRect rect, SignatureRole role) {
        return new PdfField(PdfFieldKind.SIGNATURE, rect, null, 10, TextAlign.LEFT, false, role);
    }

    private PdfStamper.Stamped stamp(byte[] original, PdfField... fields) throws IOException {
        return stamp(original, 2, fields);
    }

    private PdfStamper.Stamped stamp(byte[] original, int guardians, PdfField... fields) throws IOException {
        return stamper.stamp(original, new PdfLayout(List.of(fields), List.of()), guardians, FILL);
    }

    private static String drawn(List<TextPosition> positions) {
        var text = new StringBuilder();
        positions.forEach(position -> text.append(position.getUnicode()));
        return text.toString();
    }

    @Test
    void aTextLandsInItsBoxOnItsPage() throws IOException {
        var rect = new FieldRect(2, 100, 700, 200, 20);

        var stamped = stamp(TestPdfs.plain(2), text(rect, "{{member.fullName}}", TextAlign.LEFT, false));

        assertTrue(TestPdfs.positions(stamped.pdf(), 1).isEmpty());
        var positions = TestPdfs.positions(stamped.pdf(), 2);
        assertEquals("Lena Schmidt", drawn(positions));
        var first = positions.getFirst().getTextMatrix();
        assertEquals(100 + FittedText.PADDING, first.getTranslateX(), 0.01);
        assertTrue(first.getTranslateY() > 700 && first.getTranslateY() < 720, "on the baseline inside the box");
        assertEquals(12, first.getScaleX(), 0.01);
        assertTrue(stamped.unprintable().isEmpty());
    }

    @Test
    void aTextSitsLeftCentredOrRight() throws IOException {
        var left = new FieldRect(1, 100, 700, 200, 20);
        var right = new FieldRect(1, 100, 600, 200, 20);
        var centre = new FieldRect(1, 100, 500, 200, 20);

        var stamped = stamp(
                TestPdfs.plain(1),
                text(left, "Lena", TextAlign.LEFT, false),
                text(right, "Lena", TextAlign.RIGHT, false),
                text(centre, "Lena", TextAlign.CENTER, false));

        var positions = TestPdfs.positions(stamped.pdf(), 1);
        float leftX = startOf(positions, 700);
        float rightEnd = endOf(positions, 600);
        float centreStart = startOf(positions, 500);
        float centreEnd = endOf(positions, 500);
        assertEquals(100 + FittedText.PADDING, leftX, 0.01);
        assertEquals(300 - FittedText.PADDING, rightEnd, 0.5);
        assertEquals(200, (centreStart + centreEnd) / 2, 0.5);
    }

    private static float startOf(List<TextPosition> positions, float boxBottom) {
        return lineOf(positions, boxBottom).getFirst().getTextMatrix().getTranslateX();
    }

    private static float endOf(List<TextPosition> positions, float boxBottom) {
        var last = lineOf(positions, boxBottom).getLast();
        return last.getTextMatrix().getTranslateX() + last.getWidth();
    }

    private static List<TextPosition> lineOf(List<TextPosition> positions, float boxBottom) {
        return positions.stream()
                .filter(position -> {
                    float y = position.getTextMatrix().getTranslateY();
                    return y > boxBottom && y < boxBottom + 20;
                })
                .toList();
    }

    /** A box is stored in the page's own coordinates; a crop box that does not start at zero is in them. */
    @Test
    void aCroppedPageKeepsTheBoxWhereItWasStored() throws IOException {
        var crop = new PDRectangle(50, 80, 400, 600);
        var rect = new FieldRect(1, 120, 400, 150, 20);

        var stamped = stamp(TestPdfs.turned(0, crop), text(rect, "Lena", TextAlign.LEFT, false));

        var first = TestPdfs.positions(stamped.pdf(), 1).getFirst().getTextMatrix();
        assertEquals(120 + FittedText.PADDING, first.getTranslateX() + crop.getLowerLeftX(), 0.01);
        float y = first.getTranslateY() + crop.getLowerLeftY();
        assertTrue(y > 400 && y < 420, "on the baseline inside the box");
    }

    /** On a page shown a quarter turn clockwise, the text turns with it and reads upright. */
    @Test
    void aTurnedPageGetsItsTextUpright() throws IOException {
        var rect = new FieldRect(1, 100, 200, 30, 150);

        var stamped =
                stamp(TestPdfs.turned(90, PDRectangle.A4), text(rect, "{{member.fullName}}", TextAlign.LEFT, false));

        var first = TestPdfs.positions(stamped.pdf(), 1).getFirst().getTextMatrix();
        assertEquals(0, first.getScaleX(), 0.01);
        assertEquals(12, first.getShearY(), 0.01);
        assertEquals(200 + FittedText.PADDING, first.getTranslateY(), 0.01);
        assertTrue(first.getTranslateX() > 100 && first.getTranslateX() < 130, "inside the box");
    }

    @Test
    void aPageTurnedHalfWayOrThreeQuartersReadsUprightToo() throws IOException {
        var rect = new FieldRect(1, 100, 200, 150, 30);
        var tall = new FieldRect(1, 100, 200, 30, 150);

        var half = TestPdfs.positions(
                        stamp(TestPdfs.turned(180, PDRectangle.A4), text(rect, "Lena", TextAlign.LEFT, false))
                                .pdf(),
                        1)
                .getFirst()
                .getTextMatrix();
        var threeQuarters = TestPdfs.positions(
                        stamp(TestPdfs.turned(270, PDRectangle.A4), text(tall, "Lena", TextAlign.LEFT, false))
                                .pdf(),
                        1)
                .getFirst()
                .getTextMatrix();

        assertEquals(-12, half.getScaleX(), 0.01);
        assertEquals(250 - FittedText.PADDING, half.getTranslateX(), 0.01);
        assertEquals(-12, threeQuarters.getShearY(), 0.01);
        assertEquals(350 - FittedText.PADDING, threeQuarters.getTranslateY(), 0.01);
    }

    /** A glyph the font lacks is left out and named rather than stopping the document. */
    @Test
    void aCharacterNoFontPrintsIsLeftOutAndNamed() throws IOException {
        var rect = new FieldRect(1, 100, 700, 300, 20);

        var stamped = stamp(TestPdfs.plain(1), text(rect, "Łukasz 漢 Ørsted", TextAlign.LEFT, false));

        assertEquals(List.of("漢"), stamped.unprintable());
        String text = Objects.requireNonNull(PdfText.extract(stamped.pdf()));
        assertTrue(text.contains("Łukasz"), text);
        assertTrue(text.contains("Ørsted"), text);
    }

    @Test
    void aWrappingFieldBreaksALongTextIntoLines() throws IOException {
        var rect = new FieldRect(1, 100, 600, 80, 60);

        var stamped = stamp(
                TestPdfs.plain(1), text(rect, "Dönhoffstraße 31\n10318 Berlin Lichtenberg", TextAlign.LEFT, true));

        var baselines = TestPdfs.positions(stamped.pdf(), 1).stream()
                .map(position -> Math.round(position.getTextMatrix().getTranslateY()))
                .distinct()
                .toList();
        assertTrue(baselines.size() >= 2, "several lines: " + baselines);
        assertTrue(baselines.stream().allMatch(y -> y > 600 && y < 660), "inside the box: " + baselines);
    }

    @Test
    void aCheckFieldCrossesWhereItsTextSaysYes() throws IOException {
        var yes = new FieldRect(1, 100, 700, 20, 20);
        var no = new FieldRect(1, 200, 700, 20, 20);
        var empty = new FieldRect(1, 300, 700, 20, 20);

        var picture = TestPdfs.picture(
                stamp(TestPdfs.plain(1), check(yes, "{{yes}}"), check(no, "{{no}}"), check(empty, "{{missing}}"))
                        .pdf());

        assertTrue(TestPdfs.darkAt(picture, 110, 710));
        assertFalse(TestPdfs.darkAt(picture, 210, 710));
        assertFalse(TestPdfs.darkAt(picture, 310, 710));
    }

    /** Signature fields are real, empty and named after their signer, and print as a line. */
    @Test
    void signatureFieldsAreEmptyFieldsNamedByRole() throws IOException {
        var participant = new FieldRect(1, 60, 100, 150, 40);
        var guardian = new FieldRect(2, 300, 100, 150, 40);

        var stamped = stamp(
                TestPdfs.plain(2),
                signature(participant, SignatureRole.PARTICIPANT),
                signature(guardian, SignatureRole.GUARDIAN_1));

        try (var document = Loader.loadPDF(stamped.pdf())) {
            var form = document.getDocumentCatalog().getAcroForm(null);
            assertNotNull(form);
            assertEquals(2, form.getFields().size());
            var field = (PDSignatureField) form.getField("guardian1");
            assertNull(field.getSignature());
            var widget = field.getWidgets().getFirst();
            assertTrue(widget.isPrinted());
            assertEquals(300, widget.getRectangle().getLowerLeftX(), 0.01);
            assertEquals(150, widget.getRectangle().getWidth(), 0.01);
            assertTrue(document.getPage(1).getAnnotations().stream()
                    .anyMatch(annotation -> annotation.getCOSObject() == widget.getCOSObject()));
            assertNotNull(form.getField("participant"));
            assertFalse(form.getNeedAppearances());
        }
        assertTrue(TestPdfs.darkAt(TestPdfs.picture(stamped.pdf()), 130, 100.3f), "the line to sign on");
    }

    /** Every guardian signs in a field of their own, the box shared out side by side. */
    @Test
    void eachGuardianGetsAFieldOfTheirOwnInTheBox() throws IOException {
        var box = new FieldRect(1, 100, 100, 300, 40);

        var two = stamp(TestPdfs.plain(1), 2, signature(box, SignatureRole.EACH_GUARDIAN));
        var one = stamp(TestPdfs.plain(1), 1, signature(box, SignatureRole.EACH_GUARDIAN));

        try (var document = Loader.loadPDF(two.pdf())) {
            var form = document.getDocumentCatalog().getAcroForm(null);
            assertEquals(2, form.getFields().size());
            var first = form.getField("guardian1").getWidgets().getFirst().getRectangle();
            var second = form.getField("guardian2").getWidgets().getFirst().getRectangle();
            assertEquals(100, first.getLowerLeftX(), 0.01);
            assertEquals(400, second.getUpperRightX(), 0.01);
            assertTrue(first.getUpperRightX() < second.getLowerLeftX(), "the two lines do not touch");
        }
        try (var document = Loader.loadPDF(one.pdf())) {
            var form = document.getDocumentCatalog().getAcroForm(null);
            assertEquals(1, form.getFields().size());
            assertEquals(
                    300,
                    form.getField("guardian1")
                            .getWidgets()
                            .getFirst()
                            .getRectangle()
                            .getWidth(),
                    0.01);
        }
    }

    /** A PDF that already has a line to sign on gets the field without a second line over it. */
    @Test
    void aSignatureFieldWithoutItsLineStaysAFieldAndDrawsNothing() throws IOException {
        var rect = new FieldRect(1, 60, 100, 150, 40);
        var stamped = stamp(
                TestPdfs.plain(1),
                new PdfField(
                        PdfFieldKind.SIGNATURE,
                        rect,
                        null,
                        10,
                        TextAlign.LEFT,
                        false,
                        SignatureRole.PARTICIPANT,
                        null,
                        FontStyle.REGULAR,
                        true,
                        false));

        try (var document = Loader.loadPDF(stamped.pdf())) {
            assertNotNull(document.getDocumentCatalog().getAcroForm(null).getField("participant"));
        }
        assertFalse(TestPdfs.darkAt(TestPdfs.picture(stamped.pdf()), 130, 100.3f), "no line of its own");
    }

    /** The text of a signature field prints under the line only where it is asked to, the line above it. */
    @Test
    void aSignatureFieldPrintsItsTextUnderTheLineWhereAsked() throws IOException {
        var rect = new FieldRect(1, 60, 100, 150, 40);
        var printed = stamp(TestPdfs.plain(1), signatureWithText(rect, true));
        var kept = stamp(TestPdfs.plain(1), signatureWithText(rect, false));

        assertEquals("Unterschrift", drawn(TestPdfs.positions(printed.pdf(), 1)).strip());
        assertTrue(TestPdfs.darkAt(TestPdfs.picture(printed.pdf()), 130, 113.3f), "the line raised above the text");
        assertEquals("", drawn(TestPdfs.positions(kept.pdf(), 1)).strip());
        assertTrue(TestPdfs.darkAt(TestPdfs.picture(kept.pdf()), 130, 100.3f), "the line at the bottom");
    }

    private static PdfField signatureWithText(FieldRect rect, boolean printText) {
        return new PdfField(
                PdfFieldKind.SIGNATURE,
                rect,
                "Unterschrift",
                10,
                TextAlign.LEFT,
                false,
                SignatureRole.PARTICIPANT,
                null,
                FontStyle.REGULAR,
                false,
                printText);
    }

    /** Any guardian signs in one field; a second guardian the member lacks has no field and no line. */
    @Test
    void oneGuardianSignsOnceAndAnAbsentSecondGuardianNotAtAll() throws IOException {
        var stamped = stamp(
                TestPdfs.plain(1),
                1,
                signature(new FieldRect(1, 60, 300, 150, 40), SignatureRole.ANY_GUARDIAN),
                signature(new FieldRect(1, 300, 100, 150, 40), SignatureRole.GUARDIAN_2));

        try (var document = Loader.loadPDF(stamped.pdf())) {
            var form = document.getDocumentCatalog().getAcroForm(null);
            assertEquals(1, form.getFields().size());
            assertNotNull(form.getField("anyGuardian"));
        }
        assertFalse(TestPdfs.darkAt(TestPdfs.picture(stamped.pdf()), 370, 100.3f), "no line for nobody");
    }

    /** The PDF's own form is filled and flattened; bound text is drawn like any field. */
    @Test
    void theFormOfThePdfIsFilledAndFlattened() throws IOException {
        var bindings =
                List.of(new FormBinding("person.name", "{{member.fullName}}"), new FormBinding("agree", "{{yes}}"));

        var stamped = stamper.stamp(TestPdfs.withForm(), new PdfLayout(List.of(), bindings), 0, FILL);

        try (var document = Loader.loadPDF(stamped.pdf())) {
            var form = document.getDocumentCatalog().getAcroForm(null);
            assertTrue(form == null || form.getFields().isEmpty(), "flattened");
            assertTrue(document.getPage(0).getAnnotations().isEmpty());
        }
        String text = Objects.requireNonNull(PdfText.extract(stamped.pdf()));
        assertTrue(text.contains("Lena Schmidt"), text);
        assertFalse(text.contains("Alt"), text);
        var box = TestPdfs.AGREE_BOX;
        assertTrue(
                TestPdfs.darkAt(TestPdfs.picture(stamped.pdf()), box.getLowerLeftX() + 10, box.getLowerLeftY() + 10));
    }

    @Test
    void anUnboundFormKeepsWhatItShowsAndIsFlattenedAllTheSame() throws IOException {
        var stamped = stamper.stamp(
                TestPdfs.withForm(), new PdfLayout(List.of(), List.of(new FormBinding("agree", "{{no}}"))), 0, FILL);

        String text = Objects.requireNonNull(PdfText.extract(stamped.pdf()));
        assertTrue(text.contains("Alt"), text);
        var box = TestPdfs.AGREE_BOX;
        assertFalse(
                TestPdfs.darkAt(TestPdfs.picture(stamped.pdf()), box.getLowerLeftX() + 10, box.getLowerLeftY() + 10));
    }

    /** A field kept from an earlier version of the PDF on a page the new one lacks is left out. */
    @Test
    void aFieldOnAPageThePdfLacksIsLeftOut() throws IOException {
        var stamped = stamp(
                TestPdfs.plain(1),
                text(new FieldRect(3, 100, 700, 200, 20), "Lena", TextAlign.LEFT, false),
                signature(new FieldRect(3, 100, 100, 200, 40), SignatureRole.ISSUER));

        try (var document = Loader.loadPDF(stamped.pdf())) {
            assertEquals(1, document.getNumberOfPages());
            assertNull(document.getDocumentCatalog().getAcroForm(null));
        }
    }

    @Test
    void anOwnerPasswordIsTakenOffAndAUserPasswordRefused() throws IOException {
        var stamped = stamp(
                TestPdfs.protectedBy("owner", ""),
                text(new FieldRect(1, 100, 700, 200, 20), "Lena", TextAlign.LEFT, false));

        try (var document = Loader.loadPDF(stamped.pdf())) {
            assertFalse(document.isEncrypted());
        }
        byte[] locked = TestPdfs.protectedBy("owner", "user");
        assertEquals(
                DocumentRefusal.DOCUMENT_TEMPLATE_PDF_PASSWORD,
                assertThrows(RefusalResponse.class, () -> PdfFiles.open(locked)).refusal());
        assertEquals(
                DocumentRefusal.DOCUMENT_TEMPLATE_PDF_UNREADABLE,
                assertThrows(RefusalResponse.class, () -> PdfFiles.open("no pdf".getBytes()))
                        .refusal());
    }

    private static PdfField inFamily(FieldRect rect, String text, String family, FontStyle style) {
        return new PdfField(PdfFieldKind.TEXT, rect, text, 12, TextAlign.LEFT, false, null, family, style);
    }

    /**
     * A field naming a family draws in its file, embedded as a subset; what the family lacks falls back
     * to Liberation Sans, and only what neither prints is reported.
     */
    @Test
    void aFieldDrawsInTheFamilyItNamesAsASubset() throws IOException {
        var asked = new ArrayList<String>();
        StampFonts.FieldFonts fonts = (family, style) -> {
            asked.add(family + "/" + style);
            return family.equals("Lisu") ? Optional.of(TestFonts.lisu()) : Optional.empty();
        };
        var layout = new PdfLayout(
                List.of(
                        inFamily(
                                new FieldRect(1, 100, 700, 300, 20),
                                TestFonts.LISU_TEXT + " Lena 漢",
                                "Lisu",
                                FontStyle.BOLD),
                        inFamily(new FieldRect(1, 100, 650, 300, 20), TestFonts.LISU_TEXT, "Lisu", FontStyle.BOLD),
                        inFamily(new FieldRect(1, 100, 600, 300, 20), "Lena", "Weg", FontStyle.REGULAR)),
                List.of());

        var stamped = stamper.stamp(TestPdfs.plain(1), layout, 0, FILL, fonts);

        assertEquals(List.of("漢"), stamped.unprintable());
        assertEquals(List.of("Lisu/BOLD", "Weg/REGULAR"), asked);
        var names = PdfFonts.namesIn(stamped.pdf());
        var lisu = names.stream()
                .filter(name -> name.endsWith("+" + TestFonts.LISU_POSTSCRIPT))
                .findFirst();
        assertTrue(lisu.isPresent(), names::toString);
        assertEquals(7, lisu.get().indexOf('+') + 1, "a six letter subset tag");
        assertTrue(names.stream().anyMatch(name -> name.contains("LiberationSans")), names::toString);
    }

    /** In the default font the same letters cannot be printed, which is what the preview lists. */
    @Test
    void theUnprintableListFollowsTheChosenFont() throws IOException {
        var stamped = stamp(
                TestPdfs.plain(1),
                text(new FieldRect(1, 100, 700, 300, 20), TestFonts.LISU_TEXT, TextAlign.LEFT, false));
        assertEquals(
                TestFonts.LISU_TEXT.codePoints().mapToObj(Character::toString).toList(), stamped.unprintable());
    }

    /** A file whose licence forbids subsetting is embedded whole rather than left out. */
    @Test
    void aFontThatMayNotBeSubsetIsEmbeddedWhole() throws IOException {
        byte[] whole = TestFonts.withFsType(TestFonts.lisu(), 0x0100);
        var stamped = stamper.stamp(
                TestPdfs.plain(1),
                new PdfLayout(
                        List.of(inFamily(
                                new FieldRect(1, 100, 700, 300, 20), TestFonts.LISU_TEXT, "Lisu", FontStyle.REGULAR)),
                        List.of()),
                0,
                FILL,
                (family, style) -> Optional.of(whole));
        assertTrue(stamped.unprintable().isEmpty());
        assertTrue(PdfFonts.namesIn(stamped.pdf()).contains(TestFonts.LISU_POSTSCRIPT));
    }

    private static PdfField noFamily(FieldRect rect, String text, FontStyle style) {
        return new PdfField(PdfFieldKind.TEXT, rect, text, 12, TextAlign.LEFT, false, null, null, style);
    }

    private static PdfStamper.Stamped stampWithDefault(Path directory, PdfField field) throws IOException {
        var stamper = new PdfStamper(new StampFonts(TestFonts.defaultFontIn(directory)));
        return stamper.stamp(
                TestPdfs.plain(1), new PdfLayout(List.of(field), List.of()), 0, FILL, StampFonts.FieldFonts.NONE);
    }

    private static boolean embedsLisu(PdfStamper.Stamped stamped) throws IOException {
        return PdfFonts.namesIn(stamped.pdf()).stream().anyMatch(name -> name.endsWith(TestFonts.LISU_POSTSCRIPT));
    }

    /** A field naming no family draws in the default font, with Liberation Sans behind it. */
    @Test
    void aFieldNamingNoFamilyDrawsInTheDefaultFont(@TempDir Path directory) throws IOException {
        var stamped = stampWithDefault(
                directory, noFamily(new FieldRect(1, 100, 700, 300, 20), TestFonts.LISU_TEXT, FontStyle.REGULAR));

        assertTrue(stamped.unprintable().isEmpty(), stamped.unprintable()::toString);
        assertTrue(embedsLisu(stamped));
    }

    /** A family whose file cannot be had prints in the default font, as a field naming none would. */
    @Test
    void aFamilyThatIsGoneDrawsInTheDefaultFont(@TempDir Path directory) throws IOException {
        var stamped = stampWithDefault(
                directory,
                inFamily(new FieldRect(1, 100, 700, 300, 20), TestFonts.LISU_TEXT, "Weg", FontStyle.REGULAR));

        assertTrue(stamped.unprintable().isEmpty(), stamped.unprintable()::toString);
        assertTrue(embedsLisu(stamped));
    }

    /** A style the default font lacks is drawn in Liberation Sans, never as another style of it. */
    @Test
    void aStyleTheDefaultFontLacksFallsBackToLiberationSans(@TempDir Path directory) throws IOException {
        var stamped = stampWithDefault(
                directory, noFamily(new FieldRect(1, 100, 700, 300, 20), TestFonts.LISU_TEXT, FontStyle.ITALIC));

        assertEquals(
                TestFonts.LISU_TEXT.codePoints().mapToObj(Character::toString).toList(), stamped.unprintable());
        assertFalse(embedsLisu(stamped));
    }
}
