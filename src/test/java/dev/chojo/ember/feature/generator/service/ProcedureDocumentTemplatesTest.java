/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.procedure.service.DocumentStepTemplates;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcedureDocumentTemplatesTest extends GeneratorTestBase {
    private static Wiring wiring;
    private static Wiring elsewhere;
    private static StationMember author;
    private static ProcedureDocumentTemplates steps;

    @BeforeAll
    static void setup() {
        wiring = wire("Procedure Documents");
        elsewhere = wire("Procedure Documents Elsewhere");
        author = wiring.member("procedure.documents@test.com", "Pia", "Autorin");
        steps = new ProcedureDocumentTemplates(wiring.templates(), wiring.generator());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(wiring.station().id());
        stationRepo.delete(elsewhere.station().id());
    }

    @Test
    void aLetterTheMemberSignsIsSuitable() {
        var template = create(wiring, letter("Fotoerlaubnis").body(signedBy(SignatureRole.PARTICIPANT)));
        assertTrue(steps.suitable(wiring.station().id(), template));
    }

    @Test
    void aLetterAGuardianSignsIsSuitable() {
        var template = create(wiring, letter("Elternbrief").body(signedBy(SignatureRole.EACH_GUARDIAN)));
        assertTrue(steps.suitable(wiring.station().id(), template));
    }

    @Test
    void aLetterNobodyOfTheMemberSignsIsNot() {
        var template = create(wiring, letter("Bescheinigung", "Hiermit wird bescheinigt."));
        assertFalse(steps.suitable(wiring.station().id(), template));
    }

    @Test
    void anAppointmentDocumentIsNot() {
        var template = create(
                wiring,
                letter("Teilnahme").body(signedBy(SignatureRole.PARTICIPANT)).forAppointments(true));
        assertFalse(steps.suitable(wiring.station().id(), template));
    }

    @Test
    void aTemplateOfAnotherStationOrNoneAtAllIsNot() {
        var template = create(elsewhere, letter("Fremd").body(signedBy(SignatureRole.PARTICIPANT)));
        assertFalse(steps.suitable(wiring.station().id(), template));
        assertFalse(steps.suitable(wiring.station().id(), -1));
    }

    @Test
    void aStepNamesItsDocumentByTheTemplatesName() {
        var template = create(wiring, letter("Einverständnis").body(signedBy(SignatureRole.PARTICIPANT)));
        assertEquals(
                List.of(new DocumentStepTemplates.StepDocument(template, "Einverständnis")),
                steps.named(wiring.station().id(), List.of(template, template, -1)));
    }

    @Test
    void aStationDoesNotReadTheNamesOfAnotherStationsTemplates() {
        var template = create(elsewhere, letter("Fremd benannt").body(signedBy(SignatureRole.PARTICIPANT)));
        assertEquals(List.of(), steps.named(wiring.station().id(), List.of(template)));
    }

    private static List<BlockRowRequest> signedBy(SignatureRole signer) {
        return List.of(row(text("{{member.fullName}} stimmt zu.")), row(signature(signer, "Unterschrift")));
    }

    private static int create(Wiring at, TemplateRequestBuilder request) {
        return at.templates().create(at.owner(), request.build(), author.id()).id();
    }
}
