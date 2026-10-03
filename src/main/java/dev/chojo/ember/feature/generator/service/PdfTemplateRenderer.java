/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.service.pdf.PdfStamper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.UnaryOperator;

/**
 * Turns a PDF template and the values of one member into the filled-in PDF.
 *
 * <p>The PDF is read from the station's storage as it was uploaded and filled by {@link PdfStamper}. A
 * template no PDF was uploaded for generates nothing; one whose stored file cannot be read or filled
 * fails like a letter Typst could not produce.
 */
@Singleton
public class PdfTemplateRenderer {
    private static final Logger log = LoggerFactory.getLogger(PdfTemplateRenderer.class);

    private final PdfTemplateService pdfs;
    private final PdfStamper stamper;

    @Inject
    public PdfTemplateRenderer(PdfTemplateService pdfs, PdfStamper stamper) {
        this.pdfs = pdfs;
        this.stamper = stamper;
    }

    /**
     * Fills the PDF of a template.
     *
     * @param stationId the station that keeps the PDF
     * @param content   the PDF and what is laid over it
     * @param fill      fills the placeholders of a text
     * @return the filled-in PDF and the characters no font could print
     */
    public PdfStamper.Stamped render(int stationId, PdfContent content, UnaryOperator<String> fill) {
        var original = content.original();
        if (original == null) throw DocumentRefusal.DOCUMENT_TEMPLATE_PDF_MISSING.raise();
        byte[] data = pdfs.read(stationId, original).orElseThrow(DocumentRefusal.DOCUMENT_RENDER_FAILED::raise);
        try {
            return stamper.stamp(data, content.layout(), fill);
        } catch (IOException e) {
            log.error("The PDF {} of station {} could not be filled", original.id(), stationId, e);
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        }
    }
}
