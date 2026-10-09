/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.generator.service.DocumentGeneratorService;
import dev.chojo.ember.feature.signing.entity.SigningStatements;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Reads the statements of a generated document from its template as the template stands, matched to the
 * member the way a document drawn for them now would be. The template is read rather than the document,
 * because a PDF signature field carries no statement; asking right after generating, as the screens do,
 * reads the very template the document was drawn from.
 */
@Singleton
public class TemplateDocumentStatements implements DocumentStatements {
    private final DocumentGeneratorService generator;

    @Inject
    public TemplateDocumentStatements(DocumentGeneratorService generator) {
        this.generator = generator;
    }

    @Override
    public SigningStatements of(int templateId, int memberId, String memberName) {
        return SigningStatements.of(generator.fieldStatements(templateId, memberId), memberName);
    }
}
