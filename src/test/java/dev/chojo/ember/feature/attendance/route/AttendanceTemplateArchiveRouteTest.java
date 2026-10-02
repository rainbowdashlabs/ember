/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.service.AttendanceAudienceService;
import dev.chojo.ember.feature.attendance.service.AttendanceExportService;
import dev.chojo.ember.feature.attendance.service.AttendanceReportService;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.attendance.service.AttendanceTemplateGuards;
import dev.chojo.ember.feature.attendance.service.MemberCheckNotesService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.IntStream;

import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Deleting an attendance template over HTTP: the template leaves every list a template is chosen
 * from, its sheets still open as before, and it takes no new sheet.
 */
class AttendanceTemplateArchiveRouteTest extends RepositoryTestBase {
    private static AttendanceService attendanceService;
    private static RouteHarness harness;
    private static Station station;
    private static Account account;
    private static StationMember member;

    private int templateId;
    private int sheetId;

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
                new AttendanceAudienceService(attendanceRepo),
                memberEligibility,
                new AttendanceTemplateGuards(attendanceRepo));
        harness = RouteHarness.serving(new AttendanceRoutes(
                        attendanceService,
                        mock(MemberCheckNotesService.class),
                        mock(AttendanceExportService.class),
                        mock(AttendanceReportService.class),
                        mock(StationMemberService.class),
                        memberNameResolver,
                        memberIdentityFactory))
                .withStations(stationRepo);
        station = stationRepo.create("TemplateArchiveRouteStation");
        account = accountRepo.create("template-archive-route@test.com", "Otto", "Archiv");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /** A template with one sheet that has a member on it, deleted over HTTP. */
    @BeforeEach
    void deletedTemplateWithASheet() {
        templateId = attendanceService
                .createTemplate(station.id(), "Herbstlager " + System.nanoTime())
                .id();
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        sheetId = attendanceService
                .createSession(templateId, start, start.plus(2, ChronoUnit.HOURS), null, "Lager am See", null)
                .id();
        attendanceService.createEntry(sheetId, member.id(), AttendanceEntry.EntrySource.EXTRA);

        var deleted = harness.request(client -> client.delete(
                RouteHarness.PREFIX + "/attendance/templates/" + templateId,
                null,
                harness.as(sessionAt(StationPermission.ATTENDANCE_CONFIGURE))));
        assertEquals(204, deleted.code());
    }

    @Test
    void theTemplateLeavesTheListsTemplatesAreChosenFrom() {
        harness.run((server, client) -> {
            var listed = json(client.get(
                    RouteHarness.PREFIX + "/attendance/templates",
                    harness.as(sessionAt(StationPermission.ATTENDANCE_READ))));
            assertTrue(ids(listed).noneMatch(id -> id == templateId));

            var detailed = json(client.get(
                    RouteHarness.PREFIX + "/attendance/templates/detail",
                    harness.as(sessionAt(StationPermission.ATTENDANCE_READ))));
            assertTrue(ids(detailed).noneMatch(id -> id == templateId));
        });
    }

    @Test
    void itsSheetStillOpensWithItsPeople() {
        var answer = harness.request(client -> client.get(
                RouteHarness.PREFIX + "/attendance/sessions/" + sheetId,
                harness.as(sessionAt(StationPermission.ATTENDANCE_READ))));

        assertEquals(200, answer.code());
        var detail = json(answer);
        assertEquals("Lager am See", detail.get("session").get("title").asString());
        assertEquals(templateId, detail.get("session").get("templateId").asInt());
        assertEquals(member.id(), detail.get("entries").get(0).get("memberId").asInt());
    }

    @Test
    void itTakesNoNewSheet() {
        var answer = harness.request(client -> client.post(
                RouteHarness.PREFIX + "/attendance/templates/" + templateId + "/sessions",
                body("{\"title\":\"Noch ein Lager\"}"),
                harness.as(sessionAt(StationPermission.ATTENDANCE_EDIT))));

        assertEquals("AT-058", refusalOf(answer).code());
    }

    private static IntStream ids(JsonNode list) {
        var values = new ArrayList<Integer>();
        list.forEach(node -> values.add(node.get("id").asInt()));
        return values.stream().mapToInt(Integer::intValue);
    }

    private static UserSession sessionAt(StationPermission held) {
        return new UserSession(
                new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                1,
                station.id(),
                station.uid(),
                member,
                EnumSet.of(StationPermission.USER, held),
                Set.of(),
                null);
    }
}
