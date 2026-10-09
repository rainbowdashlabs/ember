/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.feature.generator.entity.RequirementSignatureState.OPEN;
import static dev.chojo.ember.feature.generator.entity.RequirementSignatureState.PAPER_CONFIRMED;
import static dev.chojo.ember.feature.generator.entity.RequirementSignatureState.SIGNED;
import static dev.chojo.ember.feature.generator.entity.RequirementSignatureState.WAIVED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a copy stands as a whole, read from its fields, and how it stands for participants and guardians:
 * the issuer's open field is the station's to sign and keeps nothing open for them, while whoever manages
 * the registrations still sees it open.
 */
class RequirementSignatureTest {
    private static final UUID REQUEST = UUID.randomUUID();

    @Test
    void anOpenFieldKeepsTheCopyOpen() {
        assertEquals(OPEN, RequirementSignature.overall(List.of(SIGNED, OPEN, WAIVED)));
    }

    @Test
    void everyFieldLetGoIsWaived() {
        assertEquals(WAIVED, RequirementSignature.overall(List.of(WAIVED, WAIVED)));
    }

    @Test
    void anyFieldOnPaperMakesItPaper() {
        assertEquals(PAPER_CONFIRMED, RequirementSignature.overall(List.of(SIGNED, PAPER_CONFIRMED, WAIVED)));
    }

    @Test
    void otherwiseItIsSigned() {
        assertEquals(SIGNED, RequirementSignature.overall(List.of(SIGNED, WAIVED)));
    }

    @Test
    void theIssuersOpenFieldIsTheStationsForParticipantsAndOpenForManagers() {
        var copy = copy(
                field(1, "participant", RequirementSignatureState.SIGNED, false),
                field(2, "issuer", RequirementSignatureState.OPEN, false));

        var seen = copy.asParticipantsSee();

        assertEquals(RequirementSignatureState.OPEN, copy.state(), "managers still see it open");
        assertEquals(RequirementSignatureState.SIGNED, seen.state(), "nothing is missing for the participant");
        assertEquals(RequirementSignatureState.BY_STATION, seen.fields().get(1).state());
        assertEquals(RequirementSignatureState.SIGNED, seen.fields().getFirst().state());
    }

    @Test
    void anOpenFieldOfTheParticipantKeepsTheCopyOpen() {
        var seen = copy(
                        field(1, "participant", RequirementSignatureState.OPEN, true),
                        field(2, "issuer", RequirementSignatureState.OPEN, false))
                .asParticipantsSee();

        assertEquals(RequirementSignatureState.OPEN, seen.state());
    }

    @Test
    void theIssuersFieldStaysOpenForTheIssuerWhoCanSignIt() {
        var seen =
                copy(field(2, "issuer", RequirementSignatureState.OPEN, true)).asParticipantsSee();

        assertEquals(RequirementSignatureState.OPEN, seen.fields().getFirst().state());
        assertEquals(RequirementSignatureState.OPEN, seen.state());
    }

    @Test
    void aCopyWithOnlyTheIssuersOpenFieldStandsAsTheStations() {
        var seen =
                copy(field(2, "issuer", RequirementSignatureState.OPEN, false)).asParticipantsSee();

        assertEquals(RequirementSignatureState.BY_STATION, seen.state());
    }

    private static RequirementSignature copy(RequirementSignatureField... fields) {
        var states =
                List.of(fields).stream().map(RequirementSignatureField::state).toList();
        return new RequirementSignature(
                1, 2, REQUEST, RequirementSignature.overall(states), List.of(fields), false, null);
    }

    private static RequirementSignatureField field(
            int id, String name, RequirementSignatureState state, boolean yours) {
        return new RequirementSignatureField(id, name, null, state, yours);
    }
}
