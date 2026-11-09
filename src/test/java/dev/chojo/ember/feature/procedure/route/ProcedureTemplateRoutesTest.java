/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.ProcedureRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.procedure.service.DocumentStepSigning;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.procedure.service.SuitableDocuments;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * A procedure template is created and renamed over HTTP together with the managing party it hands on, and a
 * party that is not of the station leaves the template as it was.
 */
class ProcedureTemplateRoutesTest extends RepositoryTestBase {
    private static Station station;
    private static Station elsewhere;
    private static StationMember manager;
    private static StationMember stranger;
    private static RouteHarness harness;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Procedure Template Routes");
        elsewhere = stationRepo.create("Procedure Template Routes Elsewhere");
        manager = stationMemberRepo.createWithoutAccount(station.id(), "Mara Nager");
        stranger = stationMemberRepo.createWithoutAccount(elsewhere.id(), "Fritz Fremd");
        var service = new ProcedureService(
                procedureRepo,
                new DomainEventBus(Set.of()),
                restrictionService,
                SuitableDocuments.NONE,
                mock(DocumentStepSigning.class),
                stationMemberRepo);
        harness = RouteHarness.serving(
                        new ProcedureRoutes(service, mock(MemberIdentityFactory.class), mock(GuardianPolicy.class)))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
    }

    /** A template is created with its party, renamed, and keeps both when a foreign party is refused. */
    @Test
    void aTemplateIsCreatedAndRenamedWithItsParty() {
        var created = json(harness.request(client -> client.post(
                PREFIX + "/procedure-templates",
                body("{\"name\": \"Aufnahme\", \"party\": " + partyOf(manager) + "}"),
                harness.as(signedIn(manager, StationPermission.PROCEDURE_MANAGER)))));
        int templateId = created.path("id").asInt();

        assertEquals("Aufnahme", created.path("name").asString());
        assertEquals(List.of(manager.id()), memberIdsOf(templateId));

        var renamed = json(harness.request(client -> client.put(
                PREFIX + "/procedure-templates/" + templateId,
                body("{\"name\": \"Neu aufnehmen\"}"),
                harness.as(signedIn(manager, StationPermission.PROCEDURE_MANAGER)))));

        assertEquals("Neu aufnehmen", renamed.path("name").asString());
        assertEquals(List.of(manager.id()), memberIdsOf(templateId));

        var refused = harness.request(client -> client.put(
                PREFIX + "/procedure-templates/" + templateId,
                body("{\"name\": \"Fremd\", \"party\": " + partyOf(stranger) + "}"),
                harness.as(signedIn(manager, StationPermission.PROCEDURE_MANAGER))));

        assertEquals(ProcedureRefusal.PROCEDURE_PARTY_NOT_OF_STATION, refusalOf(refused));
        assertEquals(
                "Neu aufnehmen",
                procedureRepo.findTemplateById(templateId).orElseThrow().name());
        assertEquals(List.of(manager.id()), memberIdsOf(templateId));
    }

    /** A template created with a party that is not of the station is not created at all. */
    @Test
    void aForeignPartyCreatesNoTemplate() {
        int before = procedureRepo.findTemplatesByStation(station.id(), true).size();

        var refused = harness.request(client -> client.post(
                PREFIX + "/procedure-templates",
                body("{\"name\": \"Fremd\", \"party\": " + partyOf(stranger) + "}"),
                harness.as(signedIn(manager, StationPermission.PROCEDURE_MANAGER))));

        assertEquals(ProcedureRefusal.PROCEDURE_PARTY_NOT_OF_STATION, refusalOf(refused));
        assertEquals(
                before, procedureRepo.findTemplatesByStation(station.id(), true).size());
    }

    private static String partyOf(StationMember member) {
        return "{\"userTypes\": [], \"groupIds\": [], \"tagIds\": [], \"memberIds\": [" + member.id()
                + "], \"mode\": \"AND\"}";
    }

    private static List<Integer> memberIdsOf(int templateId) {
        return restrictionService
                .audienceOf(RestrictionType.PROCEDURE_TEMPLATE, templateId)
                .toSelection()
                .memberIds();
    }
}
