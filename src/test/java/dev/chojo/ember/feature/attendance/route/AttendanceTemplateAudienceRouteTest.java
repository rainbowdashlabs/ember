/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.TemplateGroup;
import dev.chojo.ember.feature.attendance.service.AttendanceAudienceService;
import dev.chojo.ember.feature.attendance.service.AttendanceExportService;
import dev.chojo.ember.feature.attendance.service.AttendanceReportService;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.attendance.service.MemberCheckNotesService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * A template's user types over HTTP: who may set them, that they come back with the template, and
 * that a sheet started without an audience of its own answers with its template's.
 */
class AttendanceTemplateAudienceRouteTest extends RepositoryTestBase {
    private static AttendanceService attendanceService;
    private static RouteHarness harness;
    private static Station station;
    private static Station otherStation;
    private static Account account;
    private static StationMember member;
    private static MemberGroup group;

    private int templateId;

    @BeforeAll
    static void setupClass() {
        attendanceService = new AttendanceService(
                attendanceRepo,
                eventRepo,
                eventFieldRepo,
                eventFieldDefaultRepo,
                eventRegistrationRepo,
                stationMemberRepo,
                memberGroupRepo,
                new Attendance(),
                stationRepo,
                eventDateCancellationRepo,
                new AttendanceAudienceService(attendanceRepo));
        harness = RouteHarness.serving(new AttendanceRoutes(
                        attendanceService,
                        mock(MemberCheckNotesService.class),
                        mock(AttendanceExportService.class),
                        mock(AttendanceReportService.class),
                        mock(StationMemberService.class),
                        memberNameResolver,
                        memberIdentityFactory))
                .withStations(stationRepo);
        station = stationRepo.create("TemplateAudienceRouteStation");
        otherStation = stationRepo.create("TemplateAudienceRouteOther");
        account = accountRepo.create("template-audience-route@test.com", "Tara", "Types");
        member = stationMemberRepo.create(station.id(), account.id());
        group = memberGroupRepo.create(station.id(), "Route Gruppe");
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accountRepo.delete(account.id());
    }

    @BeforeEach
    void createTemplate() {
        templateId =
                attendanceService.createTemplate(station.id(), "Route Vorlage").id();
    }

    @AfterEach
    void deleteTemplate() {
        attendanceService.deleteTemplate(templateId);
    }

    @Test
    void aConfiguratorSetsTheUserTypesAndTheTemplateCarriesThem() {
        harness.run((server, client) -> {
            var set = client.put(
                    userTypesOf(templateId),
                    body("{\"userTypes\":[\"TEAM\",\"MANAGER\"]}"),
                    harness.as(sessionAt(station, StationPermission.ATTENDANCE_CONFIGURE)));
            assertEquals(200, set.code());
            assertEquals(Set.of("TEAM", "MANAGER"), texts(json(set)));

            var read = client.get(
                    RouteHarness.PREFIX + "/attendance/templates/" + templateId,
                    harness.as(sessionAt(station, StationPermission.ATTENDANCE_CONFIGURE)));
            assertEquals(Set.of("TEAM", "MANAGER"), texts(json(read).get("userTypes")));

            var cleared = client.put(
                    userTypesOf(templateId),
                    body("{\"userTypes\":[]}"),
                    harness.as(sessionAt(station, StationPermission.ATTENDANCE_CONFIGURE)));
            assertEquals(Set.of(), texts(json(cleared)));
        });
    }

    @Test
    void takingAttendanceIsNotConfiguringTemplates() {
        var answer = harness.request(client -> client.put(
                userTypesOf(templateId),
                body("{\"userTypes\":[\"TEAM\"]}"),
                harness.as(sessionAt(station, StationPermission.ATTENDANCE_EDIT))));

        assertEquals(403, answer.code());
        assertEquals(Set.of(), attendanceService.findTemplateUserTypes(templateId));
    }

    @Test
    void anotherStationsTemplateIsNotThere() {
        var answer = harness.request(client -> client.put(
                userTypesOf(templateId),
                body("{\"userTypes\":[\"TEAM\"]}"),
                harness.as(sessionAt(otherStation, StationPermission.ATTENDANCE_CONFIGURE))));

        assertEquals(Refusal.NOT_HERE_OR_NOT_YOURS, refusalOf(answer));
        assertEquals(Set.of(), attendanceService.findTemplateUserTypes(templateId));
    }

    @Test
    void aSheetStartedWithoutAnAudienceAnswersWithItsTemplates() {
        attendanceService.setTemplateGroups(templateId, List.of(new TemplateGroup(group.id(), 0)));
        attendanceService.setTemplateUserTypes(templateId, List.of(StationUserType.GUARDIAN));

        harness.run((server, client) -> {
            var created = client.post(
                    RouteHarness.PREFIX + "/attendance/templates/" + templateId + "/sessions",
                    body("{\"title\":\"Ohne eigenes Publikum\"}"),
                    harness.as(sessionAt(station, StationPermission.ATTENDANCE_EDIT)));
            assertEquals(201, created.code());
            int sessionId = json(created).get("id").asInt();

            var read = client.get(
                    RouteHarness.PREFIX + "/attendance/sessions/" + sessionId,
                    harness.as(sessionAt(station, StationPermission.ATTENDANCE_READ)));
            var audience = json(read).get("audience");
            assertEquals(Set.of("GUARDIAN"), texts(audience.get("userTypes")));
            assertEquals(group.id(), audience.get("groupIds").get(0).asInt());
        });
    }

    private static String userTypesOf(int templateId) {
        return RouteHarness.PREFIX + "/attendance/templates/" + templateId + "/user-types";
    }

    private static Set<String> texts(JsonNode array) {
        var values = new ArrayList<String>();
        array.forEach(node -> values.add(node.asString()));
        return Set.copyOf(values);
    }

    private static UserSession sessionAt(Station at, StationPermission held) {
        return new UserSession(
                new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                1,
                at.id(),
                at.uid(),
                member,
                EnumSet.of(StationPermission.USER, held),
                Set.of(),
                null);
    }
}
