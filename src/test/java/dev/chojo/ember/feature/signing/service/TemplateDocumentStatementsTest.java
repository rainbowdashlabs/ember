/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.FieldStatements;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.SignatureFieldName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The statements of a document are its template's, for the member it is about, in its language. */
class TemplateDocumentStatementsTest {

    @Test
    void theTemplatesWordingWinsAndTheRestIsTheDefaultInItsLanguage() {
        var generator = mock(DocumentGeneratorService.class);
        when(generator.fieldStatements(4, 9))
                .thenReturn(new FieldStatements(DocumentLanguage.EN, Map.of("guardian1", "We agree.")));

        var statements = new TemplateDocumentStatements(generator).of(4, 9, "Kim Kind");

        assertEquals("We agree.", statements.of(new SignatureFieldName("guardian1", FieldRole.GUARDIAN, 1)));
        assertEquals(
                "I hold parental responsibility for Kim Kind and agree to the content of this document in that"
                        + " capacity.",
                statements.of(new SignatureFieldName("guardian2", FieldRole.GUARDIAN, 2)));
        assertEquals(
                "I have read this document and agree to its content.",
                statements.of(new SignatureFieldName("participant", FieldRole.PARTICIPANT, 0)));
    }
}
