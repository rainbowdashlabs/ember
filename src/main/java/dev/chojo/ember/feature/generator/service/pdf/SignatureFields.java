/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.feature.generator.entity.SignatureRole;
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

/**
 * Empty PDF signature fields, one per signer, named after the signer's role.
 *
 * <p>A field is a real signature field of the document's form, left unsigned. Its own appearance is
 * empty, so a document that is never signed digitally prints whatever is drawn beneath it: the line
 * the field's box was placed on. Signing later draws into exactly this field, found by its name.
 *
 * <p>A letter places its field through Typst, which knows where the closing ended up and PDFBox does
 * not. The letter draws a box there and links it to {@code ember-signature:<role>}; the link is the one
 * thing Typst writes into a PDF with its exact rectangle. {@link #replaceMarkers} swaps each such link
 * for the signature field of that role.
 */
public final class SignatureFields {
    /** How a letter marks the box of a signature field: a link to this address followed by the role. */
    public static final String MARKER = "ember-signature:";

    private SignatureFields() {}

    /**
     * Adds an empty signature field.
     *
     * @param document the document
     * @param page     the page it sits on
     * @param rect     where it sits, in the page's unturned coordinates
     * @param role     who signs in it
     * @throws IOException where the page's annotations cannot be read
     */
    public static void add(PDDocument document, PDPage page, PDRectangle rect, SignatureRole role) throws IOException {
        var form = formOf(document);
        var field = new PDSignatureField(form);
        field.setPartialName(role.fieldName());
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
     * without markers comes back untouched.
     *
     * @param pdf the letter as Typst wrote it
     * @return the letter with its signature fields
     * @throws IOException where the letter cannot be read or written
     */
    public static byte[] replaceMarkers(byte[] pdf) throws IOException {
        try (var document = PdfFiles.open(pdf)) {
            boolean found = false;
            for (var page : document.getPages()) {
                found |= replaceMarkers(document, page);
            }
            return found ? PdfFiles.save(document) : pdf;
        }
    }

    private static boolean replaceMarkers(PDDocument document, PDPage page) throws IOException {
        var kept = new ArrayList<PDAnnotation>();
        var markers = new ArrayList<PDAnnotationLink>();
        for (var annotation : page.getAnnotations()) {
            if (annotation instanceof PDAnnotationLink link && roleOf(link).isPresent()) {
                markers.add(link);
            } else {
                kept.add(annotation);
            }
        }
        if (markers.isEmpty()) return false;
        page.setAnnotations(kept);
        for (var marker : markers) {
            var role = roleOf(marker).orElseThrow();
            if (hasField(document, role)) continue;
            add(document, page, marker.getRectangle(), role);
        }
        return true;
    }

    private static Optional<SignatureRole> roleOf(PDAnnotationLink link) {
        if (!(link.getAction() instanceof PDActionURI uri)) return Optional.empty();
        String address = uri.getURI();
        if (address == null || !address.startsWith(MARKER)) return Optional.empty();
        return SignatureRole.ofFieldName(address.substring(MARKER.length()));
    }

    private static boolean hasField(PDDocument document, SignatureRole role) {
        var form = document.getDocumentCatalog().getAcroForm(null);
        return form != null && form.getField(role.fieldName()) != null;
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
