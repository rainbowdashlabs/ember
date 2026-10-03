/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.stream.Stream;

/**
 * What a template is made of, one shape per {@link DocumentTemplateKind}: a letter written in Ember, or
 * fields laid over an uploaded PDF.
 */
public sealed interface TemplateContent permits LetterContent, PdfContent {

    /** @return the kind of template this content makes */
    DocumentTemplateKind kind();

    /** @return every text of the content that can name placeholders */
    Stream<String> texts();
}
