/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.events.service.EventGroupPartDates;
import dev.chojo.ember.feature.events.service.EventGroupPartDates.PartDate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.signing.entity.AgreementSigner;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The signers of an event group's documents: each person once per document, with the copy that got
 * furthest on any part, and nothing a part asks for on its own.
 */
class EventGroupAgreementSignersTest {
    private static final LocalDate FIRST = LocalDate.parse("2027-03-07");
    private static final LocalDate SECOND = LocalDate.parse("2027-03-08");

    private static AgreementSigner signer(int memberId, int templateId, RequirementSignatureState state) {
        return new AgreementSigner(
                memberId, null, "Mitglied " + memberId, templateId, "Dokument", state, Instant.now(), null, false);
    }

    @Test
    void everyPersonIsListedOncePerDocumentWithTheCopyThatGotFurthest() {
        var signers = mock(AgreementSigners.class);
        var documents = mock(EventRequirementRepository.class);
        var partDates = mock(EventGroupPartDates.class);
        when(documents.forEventGroup(70))
                .thenReturn(List.of(new RequiredTemplate(
                        8, "Einverständnis", DocumentTemplateKind.values()[0], 1, false, null, false)));
        when(partDates.parts(70)).thenReturn(List.of(new PartDate(101, FIRST), new PartDate(102, SECOND)));
        when(signers.of(101, FIRST))
                .thenReturn(List.of(
                        signer(11, 8, RequirementSignatureState.REVOKED),
                        signer(12, 8, RequirementSignatureState.SIGNED),
                        signer(13, 9, RequirementSignatureState.SIGNED)));
        when(signers.of(102, SECOND))
                .thenReturn(List.of(
                        signer(11, 8, RequirementSignatureState.OPEN),
                        signer(12, 8, RequirementSignatureState.OPEN),
                        signer(14, 8, RequirementSignatureState.PAPER_CONFIRMED)));

        var listed = new EventGroupAgreementSigners(signers, documents, partDates).of(70);

        assertEquals(
                List.of(11, 12, 14),
                listed.stream().map(AgreementSigner::memberId).toList());
        assertEquals(
                List.of(
                        RequirementSignatureState.OPEN,
                        RequirementSignatureState.SIGNED,
                        RequirementSignatureState.PAPER_CONFIRMED),
                listed.stream().map(AgreementSigner::state).toList());
    }
}
