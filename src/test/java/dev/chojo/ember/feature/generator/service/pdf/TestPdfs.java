/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDFs built for the tests, and the means to read back what was drawn on them.
 */
public final class TestPdfs {
    /** Where the form fields of {@link #withForm()} sit. */
    public static final PDRectangle NAME_BOX = new PDRectangle(100, 600, 200, 20);

    /** Where the check box of {@link #withForm()} sits. */
    public static final PDRectangle AGREE_BOX = new PDRectangle(100, 500, 20, 20);

    /** How many pixels a point is drawn as, enough for a line of half a point to come out solid. */
    private static final float SCALE = 4f;

    private TestPdfs() {}

    /**
     * @param pages how many A4 pages
     * @return a PDF of that many empty pages
     */
    public static byte[] plain(int pages) throws IOException {
        try (var document = new PDDocument()) {
            for (int index = 0; index < pages; index++) document.addPage(new PDPage(PDRectangle.A4));
            return bytes(document);
        }
    }

    /**
     * @param rotation the turn of its page
     * @param crop     the crop box of its page
     * @return a PDF of one A4 page, turned and cropped
     */
    public static byte[] turned(int rotation, PDRectangle crop) throws IOException {
        try (var document = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            page.setRotation(rotation);
            page.setCropBox(crop);
            document.addPage(page);
            return bytes(document);
        }
    }

    /**
     * A form: a text field {@code person.name} prefilled with "Alt", and a check box {@code agree} that is
     * off and draws a filled square when on.
     */
    public static byte[] withForm() throws IOException {
        try (var document = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            var form = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(form);
            var resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            form.setDefaultResources(resources);
            form.setDefaultAppearance("/Helv 0 Tf 0 g");

            var name = new PDTextField(form);
            name.setPartialName("name");
            name.setAlternateFieldName("Name der Person");
            name.setDefaultAppearance("/Helv 11 Tf 0 g");
            var nameWidget = name.getWidgets().getFirst();
            nameWidget.setRectangle(NAME_BOX);
            nameWidget.setPage(page);
            nameWidget.setPrinted(true);
            page.getAnnotations().add(nameWidget);

            var agree = new PDCheckBox(form);
            agree.setPartialName("agree");
            var agreeWidget = agree.getWidgets().getFirst();
            agreeWidget.setRectangle(AGREE_BOX);
            agreeWidget.setPage(page);
            agreeWidget.setPrinted(true);
            agreeWidget.setAppearance(checkAppearance(document));
            agreeWidget.getCOSObject().setName(COSName.AS, "Off");
            page.getAnnotations().add(agreeWidget);

            var person = new PDNonTerminalField(form);
            person.setPartialName("person");
            person.setChildren(List.of(name));
            name.getCOSObject().setItem(COSName.PARENT, person.getCOSObject());
            form.setFields(List.of(person, agree));
            name.setValue("Alt");
            return bytes(document);
        }
    }

    private static PDAppearanceDictionary checkAppearance(PDDocument document) throws IOException {
        var on = new PDAppearanceStream(document);
        on.setBBox(new PDRectangle(AGREE_BOX.getWidth(), AGREE_BOX.getHeight()));
        on.setResources(new PDResources());
        try (var content = new PDPageContentStream(document, on)) {
            content.setNonStrokingColor(0f);
            content.addRect(2, 2, AGREE_BOX.getWidth() - 4, AGREE_BOX.getHeight() - 4);
            content.fill();
        }
        var off = new PDAppearanceStream(document);
        off.setBBox(new PDRectangle(AGREE_BOX.getWidth(), AGREE_BOX.getHeight()));
        off.setResources(new PDResources());
        var states = new COSDictionary();
        states.setItem(COSName.getPDFName("Yes"), on);
        states.setItem(COSName.Off, off);
        var appearance = new PDAppearanceDictionary();
        appearance.setNormalAppearance(new PDAppearanceEntry(states));
        return appearance;
    }

    /**
     * @param ownerPassword the password that lifts the restrictions
     * @param userPassword  the password that opens it, empty for none
     * @return a protected PDF of one page
     */
    public static byte[] protectedBy(String ownerPassword, String userPassword) throws IOException {
        try (var document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            var permissions = new AccessPermission();
            permissions.setCanModify(false);
            var policy = new StandardProtectionPolicy(ownerPassword, userPassword, permissions);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return bytes(document);
        }
    }

    /** The bytes of a document. */
    public static byte[] bytes(PDDocument document) throws IOException {
        var out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    /**
     * Every character drawn on a page, with where it was drawn.
     *
     * @param pdf  the file
     * @param page the page, counted from one
     * @return the characters in the order they were drawn
     */
    public static List<TextPosition> positions(byte[] pdf, int page) throws IOException {
        var found = new ArrayList<TextPosition>();
        try (var document = Loader.loadPDF(pdf)) {
            var stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> textPositions) {
                    found.addAll(textPositions);
                }
            };
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            stripper.getText(document);
        }
        return found;
    }

    /**
     * @param pdf the file
     * @return its first page drawn at {@link #SCALE} pixels per point
     */
    public static BufferedImage picture(byte[] pdf) throws IOException {
        return picture(pdf, 1);
    }

    /**
     * @param pdf  the file
     * @param page the page, counted from one
     * @return that page drawn at {@link #SCALE} pixels per point
     */
    public static BufferedImage picture(byte[] pdf, int page) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFRenderer(document).renderImage(page - 1, SCALE);
        }
    }

    /**
     * @param pdf the file
     * @return how many pages it has
     */
    public static int pages(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    /**
     * Whether the pixel at a point of an unturned page is dark.
     *
     * @param picture the page drawn by {@link #picture}
     * @param x       the point's x in PDF points
     * @param y       the point's y in PDF points, from the bottom
     * @return whether it is dark
     */
    public static boolean darkAt(BufferedImage picture, float x, float y) {
        int rgb = picture.getRGB(Math.round(x * SCALE), Math.round(picture.getHeight() - y * SCALE));
        int red = (rgb >> 16) & 0xff;
        return red < 128;
    }
}
