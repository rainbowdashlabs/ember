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
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldValueEntry;
import dev.chojo.ember.feature.attendance.service.AttendanceAudienceService;
import dev.chojo.ember.feature.attendance.service.AttendanceExportService;
import dev.chojo.ember.feature.attendance.service.AttendanceReportService;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.attendance.service.AttendanceTemplateGuards;
import dev.chojo.ember.feature.attendance.service.MemberCheckNotesService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.sql.SqlSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Deleting a field of an attendance template over HTTP: the field leaves the template and every
 * new sheet, while the sheets that answered it keep the answer and still show it under its name.
 */
class AttendanceFieldArchiveRouteTest extends RepositoryTestBase {
    private static final Instant START = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);

    private static AttendanceService attendanceService;
    private static RouteHarness harness;
    private static Station station;
    private static Account account;
    private static StationMember member;

    private int templateId;
    private int fieldId;
    private int keptFieldId;
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
        station = stationRepo.create("FieldArchiveRouteStation");
        account = accountRepo.create("field-archive-route@test.com", "Frieda", "Feld");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /**
     * A template with a field that starts every sheet with an answer and a field that stays, one
     * sheet that answered both, and the first field then deleted over HTTP.
     */
    @BeforeEach
    void deletedFieldWithAnAnsweredSheet() {
        templateId = attendanceService
                .createTemplate(station.id(), "Atemschutz " + System.nanoTime())
                .id();
        fieldId = attendanceService
                .createTemplateField(
                        templateId,
                        "Thema",
                        FieldType.TEXT,
                        AttendanceFieldConfig.parse("{\"defaultValue\":\"Ausbildung\"}"),
                        0)
                .getFirst()
                .id();
        keptFieldId =
                attendanceService
                        .createTemplateField(
                                templateId, "Leitung", FieldType.TEXT, AttendanceFieldConfig.parse("{}"), 1)
                        .stream()
                        .filter(field -> field.id() != fieldId)
                        .findFirst()
                        .orElseThrow()
                        .id();
        sheetId = attendanceService
                .createSession(templateId, START, START.plus(2, ChronoUnit.HOURS), null, "Übung", null)
                .id();
        attendanceService.setSessionFields(
                sheetId,
                List.of(
                        new AttendanceFieldValueEntry(fieldId, "\"Knoten\""),
                        new AttendanceFieldValueEntry(keptFieldId, "\"Greta\"")));

        var deleted = harness.request(client -> client.delete(
                RouteHarness.PREFIX + "/attendance/templates/" + templateId + "/fields/" + fieldId,
                null,
                harness.as(sessionAt(StationPermission.ATTENDANCE_CONFIGURE))));
        assertEquals(200, deleted.code());
        assertEquals(List.of(keptFieldId), ids(json(deleted)).boxed().toList());
    }

    @Test
    void theSheetKeepsTheAnswerAndShowsItUnderTheFieldsName() {
        var detail = sheet(sheetId);

        assertEquals("\"Knoten\"", valueOf(detail.get("fields"), fieldId));
        var shown = fieldOf(detail.get("templateFields"), fieldId);
        assertEquals("Thema", shown.get("name").asString());
        assertEquals("TEXT", shown.get("fieldType").asString());
        assertEquals(
                List.of(fieldId, keptFieldId),
                ids(detail.get("templateFields")).boxed().toList());
    }

    @Test
    void theFieldIsGoneFromTheTemplatesConfiguration() {
        harness.run((server, client) -> {
            var fields = json(client.get(
                    RouteHarness.PREFIX + "/attendance/templates/" + templateId + "/fields",
                    harness.as(sessionAt(StationPermission.ATTENDANCE_CONFIGURE))));
            assertEquals(List.of(keptFieldId), ids(fields).boxed().toList());

            var template = json(client.get(
                    RouteHarness.PREFIX + "/attendance/templates/" + templateId,
                    harness.as(sessionAt(StationPermission.ATTENDANCE_CONFIGURE))));
            assertEquals(
                    List.of(keptFieldId), ids(template.get("fields")).boxed().toList());
        });
    }

    @Test
    void aNewSheetDoesNotGetTheField() {
        var created = harness.request(client -> client.post(
                RouteHarness.PREFIX + "/attendance/templates/" + templateId + "/sessions",
                body("{\"title\":\"Nächste Übung\"}"),
                harness.as(sessionAt(StationPermission.ATTENDANCE_EDIT))));
        assertEquals(201, created.code());
        int newSheet = json(created).get("id").asInt();

        var detail = sheet(newSheet);

        assertTrue(ids(detail.get("fields")).noneMatch(id -> id == fieldId));
        assertEquals(
                List.of(keptFieldId), ids(detail.get("templateFields")).boxed().toList());
    }

    @Test
    void changingTheDeletedFieldIsRefused() {
        harness.run((server, client) -> {
            var changed = client.put(
                    RouteHarness.PREFIX + "/attendance/templates/" + templateId + "/fields/" + fieldId,
                    body("{\"name\":\"Thema neu\",\"fieldType\":\"TEXT\",\"config\":{},\"position\":0}"),
                    harness.as(sessionAt(StationPermission.ATTENDANCE_CONFIGURE)));
            assertEquals("AT-059", refusalOf(changed).code());

            var deletedAgain = client.delete(
                    RouteHarness.PREFIX + "/attendance/templates/" + templateId + "/fields/" + fieldId,
                    null,
                    harness.as(sessionAt(StationPermission.ATTENDANCE_CONFIGURE)));
            assertEquals("AT-059", refusalOf(deletedAgain).code());
        });
        assertEquals("\"Knoten\"", valueOf(sheet(sheetId).get("fields"), fieldId));
    }

    @Test
    void theDatabaseRefusesToDeleteAFieldThatHasAnswers() {
        assertFalse(deletesQuietly(keptFieldId));

        assertEquals("\"Greta\"", valueOf(sheet(sheetId).get("fields"), keptFieldId));
    }

    private JsonNode sheet(int id) {
        var answer = harness.request(client -> client.get(
                RouteHarness.PREFIX + "/attendance/sessions/" + id,
                harness.as(sessionAt(StationPermission.ATTENDANCE_READ))));
        assertEquals(200, answer.code());
        return json(answer);
    }

    private static String valueOf(JsonNode answers, int field) {
        for (var answer : answers) {
            if (answer.get("fieldId").asInt() == field)
                return answer.get("value").asString();
        }
        throw new AssertionError("No answer to field " + field);
    }

    private static JsonNode fieldOf(JsonNode fields, int field) {
        for (var node : fields) {
            if (node.get("id").asInt() == field) return node;
        }
        throw new AssertionError("Field " + field + " is not shown");
    }

    private static IntStream ids(JsonNode list) {
        var values = new ArrayList<Integer>();
        list.forEach(node -> values.add(
                node.has("fieldId")
                        ? node.get("fieldId").asInt()
                        : node.get("id").asInt()));
        return values.stream().mapToInt(Integer::intValue);
    }

    /** Deletes the field row outright, the way only a mistake would, and says whether it went. */
    private static boolean deletesQuietly(int id) {
        try {
            return SqlSupport.deleteById("attendance_template_field", id);
        } catch (RuntimeException refused) {
            return false;
        }
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
