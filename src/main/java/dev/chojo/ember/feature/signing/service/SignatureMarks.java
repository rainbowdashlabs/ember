/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.generator.service.pdf.PdfFiles;
import dev.chojo.ember.feature.signing.entity.SignatureMark;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

/**
 * Draws what a signature leaves visible into its signature field, and takes earlier seals out of a
 * document that is about to be sealed afresh.
 *
 * <p>A mark is the signature picture alone, drawn onto the page inside the rectangle of the field it belongs
 * to and nowhere else, scaled to fit and centred, resting just above the line the field sits on. Nothing is
 * printed beside it: a template that wants the signer's name under the line prints it as the field's own
 * text. The field itself is taken out afterwards, so no reader offers to sign an empty field over a signature
 * that is already there; every other field stays as it was. Drawing onto the page rather than into the
 * field's own appearance is what makes the mark show in every viewer and on paper, since browser viewers
 * leave signature fields out.
 *
 * <p>A document sealed before (a letter signed for its issuer when it was generated) carries that seal in
 * a signed signature field. Any later state of it is a new document sealed once as a whole, in which the
 * earlier seal could only show as broken, so {@link #withoutSeals} takes signed fields out first, with the
 * long-term validation material that belonged to them.
 */
public final class SignatureMarks {
    private static final float GAP = 1.5f;
    private static final COSName DSS = COSName.getPDFName("DSS");

    private SignatureMarks() {}

    /**
     * Draws marks into the fields of a document.
     *
     * @param pdf   the document
     * @param marks the marks, each naming its field; a field the document does not have is passed over
     * @return the document with the marks drawn and their fields taken out
     * @throws UncheckedIOException when the document cannot be read or written
     */
    public static byte[] draw(byte[] pdf, List<SignatureMark> marks) {
        try (var document = PdfFiles.open(pdf)) {
            draw(document, marks);
            return PdfFiles.save(document);
        } catch (IOException e) {
            throw new UncheckedIOException("Signature marks could not be drawn", e);
        }
    }

    /**
     * Draws marks into the fields of an open document.
     *
     * @param document the document
     * @param marks    the marks, each naming its field; a field the document does not have is passed over
     * @throws IOException when a picture or the font cannot be read into the document
     */
    public static void draw(PDDocument document, List<SignatureMark> marks) throws IOException {
        var form = document.getDocumentCatalog().getAcroForm(null);
        if (form == null || marks.isEmpty()) return;
        for (var mark : marks) {
            if (!(form.getField(mark.fieldName()) instanceof PDSignatureField field)) continue;
            byte[] png = mark.png();
            for (var widget : field.getWidgets()) {
                var page = pageOf(document, widget);
                if (page.isEmpty() || png == null) continue;
                drawInto(document, page.get(), widget.getRectangle(), png);
            }
            remove(document, form, field);
        }
    }

    /**
     * Takes every signed signature field out of a document, with the validation material stored for the
     * seals, so the document can be sealed afresh as a whole. Unsigned fields stay.
     *
     * @param document the document
     * @return whether anything was taken out
     */
    public static boolean withoutSeals(PDDocument document) {
        var catalog = document.getDocumentCatalog();
        boolean changed = catalog.getCOSObject().containsKey(DSS);
        catalog.getCOSObject().removeItem(DSS);
        var form = catalog.getAcroForm(null);
        if (form == null) return changed;
        var signed = new ArrayList<PDSignatureField>();
        for (PDField field : form.getFieldTree()) {
            if (field instanceof PDSignatureField signature && signature.getSignature() != null) signed.add(signature);
        }
        for (var field : signed) {
            remove(document, form, field);
        }
        return changed || !signed.isEmpty();
    }

    private static void drawInto(PDDocument document, PDPage page, PDRectangle rect, byte[] png) throws IOException {
        float height = rect.getHeight() - GAP;
        if (height <= 0) return;
        try (var stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            drawPicture(document, stream, png, rect, rect.getLowerLeftY() + GAP, height);
        }
    }

    private static void drawPicture(
            PDDocument document, PDPageContentStream stream, byte[] png, PDRectangle rect, float bottom, float height)
            throws IOException {
        var picture = ImageIO.read(new ByteArrayInputStream(png));
        if (picture == null) return;
        var image = LosslessFactory.createFromImage(document, picture);
        float scale = Math.min(rect.getWidth() / image.getWidth(), height / image.getHeight());
        float width = image.getWidth() * scale;
        float drawnHeight = image.getHeight() * scale;
        float left = rect.getLowerLeftX() + (rect.getWidth() - width) / 2;
        stream.drawImage(image, left, bottom, width, drawnHeight);
    }

    /** The page a field's widget sits on, from the widget itself or, where it does not say, by search. */
    static Optional<PDPage> pageOf(PDDocument document, PDAnnotationWidget widget) throws IOException {
        @Nullable PDPage page = widget.getPage();
        if (page != null) return Optional.of(page);
        for (var candidate : document.getPages()) {
            for (var annotation : candidate.getAnnotations()) {
                if (annotation.getCOSObject() == widget.getCOSObject()) return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Takes a field out of a document's form, with its widgets from every page. */
    static void remove(PDDocument document, PDAcroForm form, PDTerminalField field) {
        try {
            for (var widget : field.getWidgets()) {
                for (var page : document.getPages()) {
                    var annotations = page.getAnnotations();
                    if (annotations.removeIf(annotation -> annotation.getCOSObject() == widget.getCOSObject())) {
                        page.setAnnotations(annotations);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The annotations of a page could not be read", e);
        }
        var parent = field.getParent();
        if (parent != null) {
            var kids = new ArrayList<>(parent.getChildren());
            kids.removeIf(kid -> kid.getCOSObject() == field.getCOSObject());
            parent.setChildren(kids);
            return;
        }
        var fields = new ArrayList<>(form.getFields());
        fields.removeIf(candidate -> candidate.getCOSObject() == field.getCOSObject());
        form.setFields(fields);
    }
}
