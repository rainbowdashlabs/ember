/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Empty PDF signature fields, one per signer, named after the signer
 * ({@link dev.chojo.ember.feature.generator.entity.SignatureRole#fieldNames}).
 *
 * <p>A field is a real signature field of the document's form, left unsigned. Its own appearance is
 * empty, so a document that is never signed digitally prints whatever is drawn beneath it: the line
 * the field's box was placed on. Signing later draws into exactly this field, found by its name.
 *
 * <p>A letter places its fields through Typst, which knows where its signature blocks ended up and
 * PDFBox does not. The letter draws a box there and links it to an address made of a marker of its own
 * and the field's name; the link is the one thing Typst writes into a PDF with its exact rectangle.
 * {@link #replaceMarkers} swaps each such link for the signature field of that name. The marker is
 * drawn anew for every document ({@link #newMarker}), so a link a template's author writes into a text
 * can never pass for a signature field.
 */
public final class SignatureFields {
    /** How every marker of a signature field starts. */
    private static final String MARKER_PREFIX = "ember-signature:";

    /** What a field's name may be made of. */
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z0-9]+");

    private SignatureFields() {}

    /**
     * @return a marker for the signature fields of one document, to be followed by a field's name
     */
    public static String newMarker() {
        return MARKER_PREFIX + UUID.randomUUID().toString().replace("-", "") + ":";
    }

    /**
     * Adds an empty signature field.
     *
     * @param document the document
     * @param page     the page it sits on
     * @param rect     where it sits, in the page's unturned coordinates
     * @param name     the name of the field, which says who signs in it
     * @throws IOException where the page's annotations cannot be read
     */
    public static void add(PDDocument document, PDPage page, PDRectangle rect, String name) throws IOException {
        var form = formOf(document);
        var field = new PDSignatureField(form);
        field.setPartialName(name);
        var widget = field.getWidgets().getFirst();
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
        fields.add(field);
        form.setFields(fields);
    }

    /**
     * Swaps every signature marker a letter left for the signature field it stands for. A document
     * without markers comes back untouched. Where two markers name one field, as in a preview showing
     * every block, the first one gets it and the other stays a line.
     *
     * @param pdf    the letter as Typst wrote it
     * @param marker the marker the letter was drawn with
     * @return the letter with its signature fields
     * @throws IOException where the letter cannot be read or written
     */
    public static byte[] replaceMarkers(byte[] pdf, String marker) throws IOException {
        try (var document = PdfFiles.open(pdf)) {
            boolean found = false;
            for (var page : document.getPages()) {
                found |= replaceMarkers(document, page, marker);
            }
            return found ? PdfFiles.save(document) : pdf;
        }
    }

    private static boolean replaceMarkers(PDDocument document, PDPage page, String marker) throws IOException {
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
            String name = nameOf(link, marker).orElseThrow();
            if (hasField(document, name)) continue;
            add(document, page, link.getRectangle(), name);
        }
        return true;
    }

    private static Optional<String> nameOf(PDAnnotationLink link, String marker) {
        if (!(link.getAction() instanceof PDActionURI uri)) return Optional.empty();
        String address = uri.getURI();
        if (address == null || !address.startsWith(marker)) return Optional.empty();
        String name = address.substring(marker.length());
        return FIELD_NAME.matcher(name).matches() ? Optional.of(name) : Optional.empty();
    }

    private static boolean hasField(PDDocument document, String name) {
        var form = document.getDocumentCatalog().getAcroForm(null);
        return form != null && form.getField(name) != null;
    }

    private static PDAcroForm formOf(PDDocument document) {
        var catalog = document.getDocumentCatalog();
        var form = catalog.getAcroForm(null);
        if (form != null) return form;
        var created = new PDAcroForm(document);
        catalog.setAcroForm(created);
        return created;
    }
}
