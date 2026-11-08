/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The font of a run is found the way Word finds it: its own properties, its character style, its
 * paragraph style, the defaults of the document and the theme, each with the styles it is based on.
 */
class RunFontsTest {
    private static final String STYLES = """
            <w:styles xmlns:w="%s">
              <w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:asciiTheme="minorHAnsi"/></w:rPr></w:rPrDefault>
              </w:docDefaults>
              <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:rPr><w:sz w:val="22"/></w:rPr></w:style>
              <w:style w:type="paragraph" w:styleId="Base"><w:rPr><w:rFonts w:ascii="Frutiger"/></w:rPr></w:style>
              <w:style w:type="paragraph" w:styleId="Heading"><w:basedOn w:val="Base"/><w:rPr><w:sz w:val="32"/></w:rPr>
              </w:style>
              <w:style w:type="character" w:styleId="Code"><w:rPr><w:rFonts w:ascii="Courier New"/></w:rPr></w:style>
            </w:styles>""".formatted(WordXml.W);

    private static final String THEME = """
            <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:themeElements><a:fontScheme>
              <a:majorFont><a:latin typeface="Calibri Light"/></a:majorFont>
              <a:minorFont><a:latin typeface="Calibri"/></a:minorFont>
            </a:fontScheme></a:themeElements></a:theme>""";

    @Test
    void aRunsOwnPropertiesComeFirst() throws IOException {
        assertEquals(
                new TextFont("Arial", 12),
                fontOfFirstRun("<w:p><w:r><w:rPr><w:rFonts w:ascii=\"Arial\"/><w:sz w:val=\"24\"/></w:rPr>"
                        + "<w:t>x</w:t></w:r></w:p>"));
    }

    @Test
    void aCharacterStyleComesBeforeTheParagraphStyle() throws IOException {
        assertEquals(
                new TextFont("Courier New", 16),
                fontOfFirstRun("<w:p><w:pPr><w:pStyle w:val=\"Heading\"/></w:pPr><w:r><w:rPr>"
                        + "<w:rStyle w:val=\"Code\"/></w:rPr><w:t>x</w:t></w:r></w:p>"));
    }

    @Test
    void aParagraphStyleIsFollowedThroughWhatItIsBasedOn() throws IOException {
        assertEquals(
                new TextFont("Frutiger", 16),
                fontOfFirstRun("<w:p><w:pPr><w:pStyle w:val=\"Heading\"/></w:pPr><w:r><w:t>x</w:t></w:r></w:p>"));
    }

    @Test
    void withoutAStyleTheDefaultParagraphStyleAndTheThemeDecide() throws IOException {
        assertEquals(new TextFont("Calibri", 11), fontOfFirstRun("<w:p><w:r><w:t>x</w:t></w:r></w:p>"));
    }

    @Test
    void withoutStylesTextIsTenPointsInNoNamedFamily() throws IOException {
        var docx = DocxPackage.read(docxOf(Map.of(DocxPackage.BODY, body("<w:p><w:r><w:t>x</w:t></w:r></w:p>"))));
        assertEquals(
                new TextFont(null, RunFonts.DEFAULT_SIZE), RunFonts.read(docx).of(firstRun(docx)));
    }

    @Test
    void aScaledFamilyIsWrittenSmallerAndAnyOtherIsLeftAlone() throws IOException {
        var parts = parts("<w:p><w:pPr><w:pStyle w:val=\"Base\"/></w:pPr><w:r><w:t>scaled</w:t></w:r>"
                + "<w:r><w:rPr><w:rFonts w:ascii=\"Arial\"/></w:rPr><w:t>kept</w:t></w:r></w:p>");
        parts.put(
                "word/header1.xml",
                ("<w:hdr xmlns:w=\"" + WordXml.W + "\"><w:p><w:pPr><w:pStyle w:val=\"Base\"/>"
                                + "</w:pPr><w:r><w:t>head</w:t></w:r></w:p></w:hdr>")
                        .getBytes(StandardCharsets.UTF_8));
        var docx = DocxPackage.read(docxOf(parts));
        var fonts = RunFonts.read(docx);

        FormFontSizes.scale(docx, fonts, Map.of("Frutiger", new FontChoice("Open Sans", 90)));

        var body = docx.xml(DocxPackage.BODY).orElseThrow();
        var runs = body.getElementsByTagNameNS(WordXml.W, "r");
        assertEquals(new TextFont("Frutiger", 9), fonts.of((Element) runs.item(0)), "10 points at 90 percent");
        assertEquals(new TextFont("Arial", 10), fonts.of((Element) runs.item(1)));
        var header = docx.xml("word/header1.xml").orElseThrow();
        assertEquals(new TextFont("Frutiger", 9), fonts.of((Element)
                header.getElementsByTagNameNS(WordXml.W, "r").item(0)));
    }

    @Test
    void boldTextOfALightCutIsSetInTheFamilyWithARealBoldFace() throws IOException {
        var docx = DocxPackage.read(docxOf(parts("<w:p><w:pPr><w:pStyle w:val=\"Base\"/></w:pPr>"
                + "<w:r><w:rPr><w:b/></w:rPr><w:t>Name:</w:t></w:r><w:r><w:t>Text</w:t></w:r>"
                + "<w:r><w:rPr><w:b w:val=\"0\"/></w:rPr><w:t>nicht fett</w:t></w:r></w:p>")));
        var fonts = RunFonts.read(docx);

        FormBoldFaces.split(docx, fonts, Map.of("Frutiger", new FontChoice("Open Sans Light", 100, "Open Sans")));

        var runs = docx.xml(DocxPackage.BODY).orElseThrow().getElementsByTagNameNS(WordXml.W, "r");
        assertEquals("Open Sans", fonts.of((Element) runs.item(0)).family());
        assertEquals("Frutiger", fonts.of((Element) runs.item(1)).family());
        assertEquals("Frutiger", fonts.of((Element) runs.item(2)).family());
    }

    private static TextFont fontOfFirstRun(String paragraphs) throws IOException {
        var docx = DocxPackage.read(docxOf(parts(paragraphs)));
        return RunFonts.read(docx).of(firstRun(docx));
    }

    private static Element firstRun(DocxPackage docx) throws IOException {
        return (Element) docx.xml(DocxPackage.BODY)
                .orElseThrow()
                .getElementsByTagNameNS(WordXml.W, "r")
                .item(0);
    }

    private static Map<String, byte[]> parts(String paragraphs) {
        var parts = new LinkedHashMap<String, byte[]>();
        parts.put(DocxPackage.BODY, body(paragraphs));
        parts.put(DocxPackage.STYLES, STYLES.getBytes(StandardCharsets.UTF_8));
        parts.put("word/theme/theme1.xml", THEME.getBytes(StandardCharsets.UTF_8));
        return parts;
    }

    private static byte[] body(String paragraphs) {
        return ("<w:document xmlns:w=\"" + WordXml.W + "\"><w:body>" + paragraphs + "</w:body></w:document>")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] docxOf(Map<String, byte[]> parts) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (var part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
