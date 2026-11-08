/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.util.LibreOffice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Turns a Word form into a PDF with the fields and questions of a PDF template laid over it.
 *
 * <p>The families the form names that are replaced are rewritten first ({@link FormFonts}). The places of
 * the form are found and named ({@link WordFormScanner}), LibreOffice writes the PDF with a form field for
 * each, their positions are read and the form fields taken off ({@link FormWidgets}), and a field of the
 * template is laid where each sat ({@link FormLayout}).
 */
public final class WordForms {
    /** The PDF export of LibreOffice with the form fields written into the PDF. */
    private static final String EXPORT_WITH_FIELDS =
            "pdf:writer_pdf_Export:{\"ExportFormFields\":{\"type\":\"boolean\",\"value\":\"true\"}}";

    private WordForms() {}

    /**
     * A Word form to convert.
     *
     * @param docx          the bytes of the Word form
     * @param choices       how each family the form names that is replaced is drawn instead
     * @param fonts         directories of fonts LibreOffice draws with besides the machine's own
     * @param fieldFamilies the family the fields of a place print in, by the family the form names for the
     *                      place; a place whose family is missing here prints in the default font
     * @param profileFields the keys of the owner's profile questions by their names in lower case
     */
    public record Form(
            byte[] docx,
            Map<String, FontChoice> choices,
            List<Path> fonts,
            Map<String, String> fieldFamilies,
            Map<String, String> profileFields) {}

    /**
     * A converted Word form.
     *
     * @param pdf        the PDF without form fields, empty where the form has no place to fill in
     * @param fields     the fields of the template, none where the form has no place to fill in
     * @param questions  the questions of the template
     * @param recognised the labels of the places that print the member's data
     * @param unplaced   the labels of the places that got no field
     * @param samples    the example value of each field for a preview, by the placeholder key it prints
     */
    public record Converted(
            byte[] pdf,
            List<PdfField> fields,
            List<TemplateQuestion> questions,
            List<String> recognised,
            List<String> unplaced,
            Map<String, String> samples) {}

    /**
     * @param docx the bytes of a Word form
     * @return the font families it names, each once
     * @throws IOException where the form cannot be read
     */
    public static List<String> fontsOf(byte[] docx) throws IOException {
        return WordFormScanner.fontsOf(docx);
    }

    /**
     * @param docx the bytes of a Word document
     * @return whether it has a place to fill in, which is what makes it a form
     * @throws IOException where the document cannot be read
     */
    public static boolean hasPlaces(byte[] docx) throws IOException {
        return !WordFormScanner.scan(docx).spots().isEmpty();
    }

    /**
     * @param form the form and how it is drawn
     * @return the PDF with what is laid over it
     * @throws IOException where the form cannot be read or LibreOffice cannot convert it
     */
    public static Converted convert(Form form) throws IOException {
        var scanned = WordFormScanner.scan(form.docx(), form.choices());
        if (scanned.spots().isEmpty()) {
            return new Converted(new byte[0], List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        byte[] pdf = LibreOffice.toPdf(scanned.docx(), "docx", EXPORT_WITH_FIELDS, form.fonts());
        var taken = FormWidgets.take(pdf);
        var laid = FormLayout.lay(
                scanned.spots(),
                taken,
                scanned.rightMargin(),
                scanned.placeFonts(),
                form.fieldFamilies(),
                form.profileFields());
        return new Converted(
                taken.pdf(), laid.fields(), laid.questions(), laid.recognised(), laid.unplaced(), laid.samples());
    }

    /**
     * The form filled in by hand, to compare with the template's fields: every place holds the example value
     * its field is filled with in a preview ({@link FormSamples}), drawn by LibreOffice in the fonts of the
     * form as the choices leave them.
     *
     * @param form the form and how it is drawn; its field families are not read
     * @return the PDF
     * @throws IOException where the form cannot be read or LibreOffice cannot convert it
     */
    public static byte[] filledByHand(Form form) throws IOException {
        var scanned = WordFormScanner.scan(form.docx(), form.choices());
        var samples = FormLayout.samples(scanned.spots(), form.profileFields());
        return LibreOffice.toPdf(FormSampleText.write(scanned.docx(), samples), "docx", null, form.fonts());
    }

    /**
     * @param docx the bytes of a Word form
     * @return the families the form carries font files for
     * @throws IOException where the form cannot be read
     */
    public static List<String> embeddedFonts(byte[] docx) throws IOException {
        return FormFonts.embedded(DocxPackage.read(docx));
    }
}
