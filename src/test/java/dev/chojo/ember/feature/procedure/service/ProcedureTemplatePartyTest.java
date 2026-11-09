/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A procedure template and the managing party it hands on are written together: both stand, or a refused
 * party leaves the template as it was.
 */
class ProcedureTemplatePartyTest extends RepositoryTestBase {
    private static Station station;
    private static int memberId;
    private static ProcedureService service;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Procedure Template Party Station");
        memberId = stationMemberRepo
                .createWithoutAccount(station.id(), "Paula Partei")
                .id();
        service = new ProcedureService(
                procedureRepo,
                new DomainEventBus(Set.of()),
                restrictionService,
                SuitableDocuments.NONE,
                mock(DocumentStepSigning.class),
                stationMemberRepo);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    /** A new template carries its party, and a renamed one the party it is given. */
    @Test
    void aTemplateIsWrittenWithItsParty() {
        var template = service.createTemplate(station.id(), "Aufnahme", null, memberId, named(memberId));

        assertEquals(List.of(memberId), partyOf(template.id()).toSelection().memberIds());

        var renamed = service.updateTemplate(station.id(), template.id(), "Neu", "Text", RestrictionAudience.empty())
                .orElseThrow();
        assertEquals("Neu", renamed.name());
        assertTrue(partyOf(template.id()).namesNobody());
        assertTrue(
                service.updateTemplate(station.id(), 999_999, "Weg", null, null).isEmpty());
    }

    /** A party naming a private tag is refused while it is written, and the rename goes with it. */
    @Test
    void aRefusedPartyLeavesTheNameAsItWas() {
        var template = service.createTemplate(station.id(), "Vorher", null, memberId, null);
        var hidden = userTagRepo.create(station.id(), "Geheim", null, TagVisibility.PRIVATE);
        var party = new RestrictionAudience(List.of(), List.of(), List.of(hidden.id()), List.of(), RestrictionMode.AND);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.updateTemplate(station.id(), template.id(), "Nachher", null, party));

        assertEquals(MemberRefusal.TAG_PRIVATE_CHOOSES_NOBODY, refused.refusal());
        assertEquals(
                "Vorher",
                procedureRepo.findTemplateById(template.id()).orElseThrow().name());
    }

    private static RestrictionAudience partyOf(int templateId) {
        return restrictionService.audienceOf(RestrictionType.PROCEDURE_TEMPLATE, templateId);
    }

    private static RestrictionAudience named(int member) {
        return new RestrictionAudience(List.of(), List.of(), List.of(), List.of(member), RestrictionMode.AND);
    }
}
