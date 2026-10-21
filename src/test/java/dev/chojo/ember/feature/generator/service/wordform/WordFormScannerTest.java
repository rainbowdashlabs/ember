/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.QuestionLimits;
import dev.chojo.ember.feature.generator.service.wordform.FormSpot.FormSpotKind;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Word form read for its places: every old form field and content control is found in document order
 * with the words that stand for it, and rewritten into a content control LibreOffice exports by its name.
 */
class WordFormScannerTest {

    private static byte[] form() throws IOException {
        try (var in = Objects.requireNonNull(
                WordFormScannerTest.class.getClassLoader().getResourceAsStream("generator/change-form.docx"))) {
            return in.readAllBytes();
        }
    }

    @Test
    void everyPlaceIsFoundWithTheWordsThatStandForIt() throws IOException {
        var scanned = WordFormScanner.scan(form());

        assertEquals(
                List.of(
                        new FormSpot(1, FormSpotKind.TEXT, "Verein", null),
                        new FormSpot(2, FormSpotKind.TEXT, "Bearbeiter", null),
                        new FormSpot(3, FormSpotKind.TEXT, "Datum", null),
                        new FormSpot(4, FormSpotKind.TEXT, "Name", null),
                        new FormSpot(5, FormSpotKind.TEXT, "Vorname", null),
                        new FormSpot(6, FormSpotKind.TEXT, "Geb.-Datum", null),
                        new FormSpot(7, FormSpotKind.TEXT, "Eintritt", null),
                        new FormSpot(8, FormSpotKind.TEXT, "Ausweisnummer", null),
                        new FormSpot(9, FormSpotKind.TEXT, "ist am", null),
                        new FormSpot(10, FormSpotKind.CHECK, "aus dem Verein ausgetreten", "false"),
                        new FormSpot(11, FormSpotKind.CHECK, "in die Gruppe … gewechselt", "false"),
                        new FormSpot(12, FormSpotKind.TEXT, "in die Gruppe", null),
                        new FormSpot(13, FormSpotKind.CHECK, "ist umgezogen. Neue Anschrift", "true"),
                        new FormSpot(14, FormSpotKind.TEXT, "Straße", null),
                        new FormSpot(15, FormSpotKind.TEXT, "PLZ Ort", null),
                        new FormSpot(16, FormSpotKind.CHECK, "Beitrag erstattet", "true"),
                        new FormSpot(17, FormSpotKind.TEXT, "Sonstiges", "keine")),
                scanned.spots());
        assertEquals(List.of("Liberation Sans"), scanned.fonts());
        assertEquals(851 / 20.0, scanned.rightMargin());
    }

    @Test
    void everyPlaceBecomesAnEmptyContentControlNamedByItsNumber() throws IOException {
        var scanned = WordFormScanner.scan(form());

        var body = DocxPackage.read(scanned.docx()).xml(DocxPackage.BODY).orElseThrow();
        var tags = new ArrayList<String>();
        var all = body.getElementsByTagNameNS(WordXml.W, "tag");
        for (int index = 0; index < all.getLength(); index++) {
            tags.add(((org.w3c.dom.Element) all.item(index)).getAttributeNS(WordXml.W, "val"));
        }
        assertEquals(17, tags.size());
        for (int number = 1; number <= 17; number++) {
            assertEquals("ember-f" + number, tags.get(number - 1));
        }
        assertEquals(0, body.getElementsByTagNameNS(WordXml.W, "fldChar").getLength());
        assertEquals(4, body.getElementsByTagNameNS(WordXml.W14, "checkbox").getLength());
        String text = body.getDocumentElement().getTextContent();
        assertTrue(!text.contains("keine") && !text.contains("☒"), "the presets are cleared from the page");
    }

    @Test
    void wordsOnTheLineBelowInTheSameParagraphNameARowOfPlaces() throws IOException {
        String field = """
                <w:r><w:fldChar w:fldCharType="begin"><w:ffData><w:name w:val="Text2"/><w:textInput/></w:ffData>\
                </w:fldChar></w:r><w:r><w:instrText> FORMTEXT </w:instrText></w:r>\
                <w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>     </w:t></w:r>\
                <w:r><w:fldChar w:fldCharType="end"/></w:r>""";
        byte[] docx = docxOf("<w:p>" + field + "<w:r><w:tab/><w:tab/></w:r>" + field
                + "<w:r><w:br/><w:t>Straße</w:t><w:tab/><w:tab/><w:t>PLZ Ort</w:t></w:r></w:p>");

        assertEquals(
                List.of(
                        new FormSpot(1, FormSpotKind.TEXT, "Straße", null),
                        new FormSpot(2, FormSpotKind.TEXT, "PLZ Ort", null)),
                WordFormScanner.scan(docx).spots());
    }

    @Test
    void whatWordLetsBeFilledInIsKept() throws IOException {
        byte[] docx = docxOf(
                "<w:p><w:r><w:t>Anzahl</w:t></w:r>" + legacy("<w:textInput><w:type w:val=\"number\"/></w:textInput>")
                        + "</w:p><w:p><w:r><w:t>Kürzel</w:t></w:r>"
                        + legacy("<w:textInput><w:maxLength w:val=\"3\"/></w:textInput>")
                        + "</w:p><w:p><w:r><w:t>Rolle</w:t></w:r>"
                        + legacy("<w:ddList><w:listEntry w:val=\"Helfer\"/><w:listEntry w:val=\"helfer\"/>"
                                + "<w:listEntry w:val=\"Leitung\"/></w:ddList>")
                        + "</w:p><w:p><w:r><w:t>Gruppe</w:t></w:r><w:sdt><w:sdtPr><w:dropDownList>"
                        + "<w:listItem w:displayText=\"Wählen Sie ein Element.\" w:value=\"\"/>"
                        + "<w:listItem w:displayText=\"Rot\" w:value=\"r\"/><w:listItem w:value=\"Blau\"/>"
                        + "</w:dropDownList><w:showingPlcHdr/></w:sdtPr><w:sdtContent><w:r><w:t>Wählen</w:t></w:r>"
                        + "</w:sdtContent></w:sdt></w:p>");

        assertEquals(
                List.of(
                        new FormSpot(1, FormSpotKind.NUMBER, "Anzahl", null),
                        new FormSpot(2, FormSpotKind.TEXT, "Kürzel", null, QuestionLimits.text(3, null)),
                        new FormSpot(
                                3,
                                FormSpotKind.CHOICE,
                                "Rolle",
                                "Helfer",
                                QuestionLimits.choice(List.of("Helfer", "Leitung"))),
                        new FormSpot(
                                4, FormSpotKind.CHOICE, "Gruppe", null, QuestionLimits.choice(List.of("Rot", "Blau")))),
                WordFormScanner.scan(docx).spots());
    }

    private static String legacy(String data) {
        return "<w:r><w:fldChar w:fldCharType=\"begin\"><w:ffData>" + data + "</w:ffData></w:fldChar></w:r>"
                + "<w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>  </w:t></w:r>"
                + "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r>";
    }

    private static byte[] docxOf(String body) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(DocxPackage.BODY));
            zip.write(("<w:document xmlns:w=\"" + WordXml.W + "\"><w:body>" + body + "</w:body></w:document>")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void aReplacedFontIsNamedNowhereInTheForm() throws IOException {
        var scanned = WordFormScanner.scan(form(), Map.of("Liberation Sans", new FontChoice("DejaVu Sans", 100)));

        assertEquals(List.of("Liberation Sans"), scanned.fonts(), "the form's own fonts are reported as written");
        assertEquals(List.of("DejaVu Sans"), WordFormScanner.fontsOf(scanned.docx()));
        assertEquals(17, scanned.spots().size());
    }

    @Test
    void everyPlaceIsReadForTheFontItIsSetInScaledAsChosen() throws IOException {
        byte[] docx = docxOf("<w:p><w:r><w:t>Name</w:t></w:r><w:r><w:rPr><w:rFonts w:ascii=\"Frutiger\"/>"
                + "<w:sz w:val=\"22\"/></w:rPr><w:fldChar w:fldCharType=\"begin\"><w:ffData><w:textInput/></w:ffData>"
                + "</w:fldChar></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>  </w:t></w:r>"
                + "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>");

        assertEquals(
                Map.of("ember-f1", new TextFont("Frutiger", 11)),
                WordFormScanner.scan(docx).placeFonts());
        assertEquals(
                Map.of("ember-f1", new TextFont("Frutiger", 10)),
                WordFormScanner.scan(docx, Map.of("Frutiger", new FontChoice("Open Sans", 90)))
                        .placeFonts(),
                "the place keeps the name the form gives its family and the size it is drawn in");
    }

    @Test
    void somethingThatIsNoWordDocumentIsRefused() {
        assertThrows(IOException.class, () -> WordFormScanner.scan("not a zip".getBytes()));
    }
}
