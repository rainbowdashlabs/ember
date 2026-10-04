/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.pdf.PdfStamper;
import dev.chojo.ember.feature.generator.service.pdf.StampFonts;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Turns a PDF template and the values of one member into the filled-in PDF.
 *
 * <p>The PDF is read from its owner's storage as it was uploaded and filled by {@link PdfStamper}. A
 * template no PDF was uploaded for generates nothing; one whose stored file cannot be read or filled
 * fails like a letter Typst could not produce. A text field naming a family draws in the file the owner
 * reaches under that name, uploaded or built in; one that is gone prints in the default font.
 */
@Singleton
public class PdfTemplateRenderer {
    private static final Logger log = LoggerFactory.getLogger(PdfTemplateRenderer.class);

    private final PdfTemplateService pdfs;
    private final PdfStamper stamper;
    private final FontLibrary fonts;

    @Inject
    public PdfTemplateRenderer(PdfTemplateService pdfs, PdfStamper stamper, FontLibrary fonts) {
        this.pdfs = pdfs;
        this.stamper = stamper;
        this.fonts = fonts;
    }

    /**
     * What every member's document of a PDF template is filled from, read once for as many documents as
     * are drawn from it: the uploaded PDF and the font files its fields draw in.
     *
     * @param owner      the station or the association that keeps the PDF
     * @param content    the PDF and what is laid over it
     * @param data       the uploaded PDF
     * @param fieldFonts the files of the families the fields name, each read the first time it is drawn in
     */
    public record Original(Owner owner, PdfContent content, byte[] data, StampFonts.FieldFonts fieldFonts) {}

    /**
     * Reads what the documents of a PDF template are filled from.
     *
     * @param owner   the station or the association that keeps the PDF and whose fonts it reaches
     * @param content the PDF and what is laid over it
     * @return the original
     */
    public Original original(Owner owner, PdfContent content) {
        var original = content.original();
        if (original == null) throw DocumentRefusal.DOCUMENT_TEMPLATE_PDF_MISSING.raise();
        byte[] data = pdfs.read(owner, original).orElseThrow(DocumentRefusal.DOCUMENT_RENDER_FAILED::raise);
        var reachable = fonts.reachable(owner);
        var read = new ConcurrentHashMap<String, Optional<byte[]>>();
        StampFonts.FieldFonts fieldFonts =
                (family, style) -> read.computeIfAbsent(family + "\n" + style, ignored -> reachable
                        .find(family)
                        .flatMap(found -> found.pdfFile(style))
                        .flatMap(fonts::read));
        return new Original(owner, content, data, fieldFonts);
    }

    /**
     * Fills the PDF of a template.
     *
     * @param owner     the station or the association that keeps the PDF and whose fonts it reaches
     * @param content   the PDF and what is laid over it
     * @param guardians how many guardians the member has, which decides how many of them sign
     * @param fill      fills the placeholders of a text
     * @return the filled-in PDF and the characters no font could print
     */
    public PdfStamper.Stamped render(Owner owner, PdfContent content, int guardians, UnaryOperator<String> fill) {
        return render(original(owner, content), guardians, fill);
    }

    /**
     * Fills a PDF read before.
     *
     * @param original  what the document is filled from
     * @param guardians how many guardians the member has, which decides how many of them sign
     * @param fill      fills the placeholders of a text
     * @return the filled-in PDF and the characters no font could print
     */
    public PdfStamper.Stamped render(Original original, int guardians, UnaryOperator<String> fill) {
        try {
            return stamper.stamp(original.data(), original.content().layout(), guardians, fill, original.fieldFonts());
        } catch (IOException e) {
            log.error("The PDF of {} could not be filled", original.owner(), e);
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        }
    }
}
