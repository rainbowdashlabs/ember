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
import java.util.function.UnaryOperator;

/**
 * Turns a PDF template and the values of one member into the filled-in PDF.
 *
 * <p>The PDF is read from its owner's storage as it was uploaded and filled by {@link PdfStamper}. A
 * template no PDF was uploaded for generates nothing; one whose stored file cannot be read or filled
 * fails like a letter Typst could not produce. A text field naming a family of uploaded fonts draws in
 * the file the owner reaches under that name; one that is gone prints in the default font.
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
     * Fills the PDF of a template.
     *
     * @param owner     the station or the association that keeps the PDF and whose fonts it reaches
     * @param content   the PDF and what is laid over it
     * @param guardians how many guardians the member has, which decides how many of them sign
     * @param fill      fills the placeholders of a text
     * @return the filled-in PDF and the characters no font could print
     */
    public PdfStamper.Stamped render(Owner owner, PdfContent content, int guardians, UnaryOperator<String> fill) {
        var original = content.original();
        if (original == null) throw DocumentRefusal.DOCUMENT_TEMPLATE_PDF_MISSING.raise();
        byte[] data = pdfs.read(owner, original).orElseThrow(DocumentRefusal.DOCUMENT_RENDER_FAILED::raise);
        var reachable = fonts.reachable(owner);
        StampFonts.FieldFonts fieldFonts = (family, style) ->
                reachable.find(family).flatMap(found -> found.pdfFile(style)).flatMap(fonts::read);
        try {
            return stamper.stamp(data, content.layout(), guardians, fill, fieldFonts);
        } catch (IOException e) {
            log.error("The PDF {} of {} could not be filled", original.id(), owner, e);
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        }
    }
}
