/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.feature.generator.entity.FillInField;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The empty text fields of a generated document that a signer fills in when they sign
 * ({@link FillInField}).
 *
 * <p>A field is a real text field of the document's form with an empty appearance, so the page shows
 * whatever is drawn beneath it, a line or a box, and a document printed unsigned is filled in by hand. Its
 * label, whether it is required and its maximum length are the field's own entries, so whatever reads the
 * document later, a PDF reader or the signing act, finds them in the file itself. Signing draws the typed
 * value onto the page in the field's place and takes the field out.
 *
 * <p>A letter places its fields through Typst the same way it places its signature fields
 * ({@link SignatureFields}): a box linked to an address made of a marker drawn anew for each document and the
 * field's name, which {@link #replaceMarkers} swaps for the text field.
 */
public final class FillInFields {
    private static final String MARKER_PREFIX = "ember-fill:";
    private static final COSName FONT = COSName.getPDFName("Helv");
    private static final String APPEARANCE = "/Helv 0 Tf 0 g";

    private FillInFields() {}

    /** @return a marker for the fields to fill in of one document, to be followed by a field's name */
    public static String newMarker() {
        return MARKER_PREFIX + UUID.randomUUID().toString().replace("-", "") + ":";
    }

    /**
     * Adds an empty field to fill in.
     *
     * @param document the document
     * @param page     the page it sits on
     * @param rect     where it sits, in the page's unturned coordinates
     * @param field    the field
     * @throws IOException where the page's annotations cannot be read
     */
    public static void add(PDDocument document, PDPage page, PDRectangle rect, FillInField field) throws IOException {
        var form = formOf(document);
        var text = new PDTextField(form);
        text.setPartialName(field.name());
        text.setAlternateFieldName(field.label());
        text.setRequired(field.required());
        text.setMaxLen(field.maxLength());
        text.setMultiline(false);
        text.setDefaultAppearance(APPEARANCE);
        var widget = text.getWidgets().getFirst();
        widget.setRectangle(rect);
        widget.setPage(page);
        widget.setPrinted(true);
        var empty = new PDAppearanceStream(document);
        empty.setBBox(new PDRectangle(rect.getWidth(), rect.getHeight()));
        empty.setResources(new PDResources());
        var appearance = new PDAppearanceDictionary();
        appearance.setNormalAppearance(empty);
        widget.setAppearance(appearance);
        page.getAnnotations().add(widget);
        var fields = new ArrayList<>(form.getFields());
        fields.add(text);
        form.setFields(fields);
    }

    /**
     * Swaps every marker a letter left for the field to fill in it stands for. A document without markers
     * comes back untouched; a marker naming a field the letter does not describe, or a field already placed,
     * stays a plain box.
     *
     * @param pdf    the letter
     * @param marker the marker the letter was drawn with
     * @param fields the fields the letter describes, by name
     * @return the letter with its fields to fill in
     * @throws IOException where the letter cannot be read or written
     */
    public static byte[] replaceMarkers(byte[] pdf, String marker, Map<String, FillInField> fields) throws IOException {
        try (var document = PdfFiles.open(pdf)) {
            boolean found = false;
            for (var page : document.getPages()) {
                found |= replaceMarkers(document, page, marker, fields);
            }
            return found ? PdfFiles.save(document) : pdf;
        }
    }

    private static boolean replaceMarkers(
            PDDocument document, PDPage page, String marker, Map<String, FillInField> fields) throws IOException {
        var kept = new ArrayList<PDAnnotation>();
        var markers = new ArrayList<PDAnnotationLink>();
        for (var annotation : page.getAnnotations()) {
            if (annotation instanceof PDAnnotationLink link
                    && nameOf(link, marker).isPresent()) {
                markers.add(link);
            } else {
                kept.add(annotation);
            }
        }
        if (markers.isEmpty()) return false;
        page.setAnnotations(kept);
        for (var link : markers) {
            var field = fields.get(nameOf(link, marker).orElseThrow());
            if (field == null || hasField(document, field.name())) continue;
            add(document, page, link.getRectangle(), field);
        }
        return true;
    }

    /**
     * The fields to fill in that a document carries.
     *
     * @param pdf the document
     * @return the fields, in the order of its form
     */
    public static List<FillInField> of(byte[] pdf) {
        try (var document = PdfFiles.open(pdf)) {
            return of(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The fields to fill in that an open document carries.
     *
     * @param document the document
     * @return the fields, in the order of its form
     */
    public static List<FillInField> of(PDDocument document) {
        var form = document.getDocumentCatalog().getAcroForm(null);
        if (form == null) return List.of();
        var found = new ArrayList<FillInField>();
        for (var field : form.getFieldTree()) {
            if (!(field instanceof PDTextField text)) continue;
            String name = text.getPartialName();
            if (name == null) continue;
            FillInField.signatureFieldOf(name)
                    .map(signatureField -> new FillInField(
                            name,
                            signatureField,
                            labelOf(text),
                            text.isRequired(),
                            FillInField.effectiveMaxLength(text.getMaxLen() > 0 ? text.getMaxLen() : null)))
                    .ifPresent(found::add);
        }
        return found;
    }

    /**
     * @param pdf            the document
     * @param signatureField the name of a signature field
     * @return the fields the signer of that signature field fills in, in the order of the document's form
     */
    public static List<FillInField> forSigner(byte[] pdf, String signatureField) {
        return of(pdf).stream()
                .filter(field -> field.signatureField().equals(signatureField))
                .toList();
    }

    private static String labelOf(PDTextField field) {
        String label = field.getAlternateFieldName();
        return label == null || label.isBlank() ? field.getPartialName() : label;
    }

    private static Optional<String> nameOf(PDAnnotationLink link, String marker) {
        if (!(link.getAction() instanceof PDActionURI uri)) return Optional.empty();
        String address = uri.getURI();
        if (address == null || !address.startsWith(marker)) return Optional.empty();
        String name = address.substring(marker.length());
        return FillInField.signatureFieldOf(name).isPresent() ? Optional.of(name) : Optional.empty();
    }

    private static boolean hasField(PDDocument document, String name) {
        var form = document.getDocumentCatalog().getAcroForm(null);
        return form != null && form.getField(name) != null;
    }

    private static PDAcroForm formOf(PDDocument document) throws IOException {
        var catalog = document.getDocumentCatalog();
        var form = catalog.getAcroForm(null);
        if (form == null) {
            form = new PDAcroForm(document);
            catalog.setAcroForm(form);
        }
        var resources = form.getDefaultResources();
        if (resources == null) {
            resources = new PDResources();
            form.setDefaultResources(resources);
        }
        if (resources.getFont(FONT) == null) {
            resources.put(FONT, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        }
        return form;
    }
}
