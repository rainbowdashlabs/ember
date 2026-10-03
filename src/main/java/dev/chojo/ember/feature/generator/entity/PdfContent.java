/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

import java.util.stream.Stream;

/**
 * What a PDF template is made of: the uploaded PDF and what is laid over it.
 *
 * @param original the version of the PDF the template fills now, or null where none was uploaded yet
 * @param layout   the fields and the form fields' values
 */
public record PdfContent(@Nullable PdfOriginal original, PdfLayout layout) implements TemplateContent {

    @Override
    public DocumentTemplateKind kind() {
        return DocumentTemplateKind.PDF;
    }

    @Override
    public Stream<String> texts() {
        return layout.texts();
    }

    @Override
    public Stream<String> fontFamilies() {
        return layout.fontFamilies();
    }
}
