/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldValueEntry;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What an attendance sheet's own fields take today, and how the sheet keeps it.
 *
 * <p>Written down before the field types are brought together, so that every later change can show
 * which of these it meant to change and that it left the rest alone.
 */
class AttendanceAnswersTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static AttendanceService service;
    private static Station station;
    private static int memberId;
    private static int groupId;

    @BeforeAll
    static void setup() {
        service = new AttendanceService(
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
        station = stationRepo.create("AttendanceAnswersStation");
        memberId = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo
                                .create("attendance-answers@test.com", "Alma", "A")
                                .id())
                .id();
        groupId = memberGroupRepo.create(station.id(), "Niemand").id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    /** Writes one answer into a fresh sheet whose only field is of the given type, and reads it back. */
    private static String kept(AttendanceFieldType type, String config, String answer) {
        var template = service.createTemplate(station.id(), "Bogen " + NAMES.incrementAndGet());
        var field = service.createTemplateField(
                        template.id(), type.name(), type, AttendanceFieldConfig.parse(config), 0)
                .getFirst();
        var start = Instant.now().plus(1, ChronoUnit.DAYS);
        var sheet = service.createSession(template.id(), start, start.plus(2, ChronoUnit.HOURS), null, null, null);
        return service.setSessionFields(sheet.id(), List.of(new AttendanceFieldValueEntry(field.id(), answer)))
                .getFirst()
                .value();
    }

    private static void refused(AttendanceFieldType type, String config, String answer) {
        assertThrows(BadRequestResponse.class, () -> kept(type, config, answer), answer + " under " + type);
    }

    @Test
    void aNumberTakesAFraction() {
        assertEquals("2.5", kept(AttendanceFieldType.NUMBER, "{}", "2.5"));
        refused(AttendanceFieldType.NUMBER, "{}", "\"zwei\"");
    }

    @Test
    void aDayAndAYesOrNoAreMeasuredAndKeptAsSent() {
        assertEquals("\"2026-03-09\"", kept(AttendanceFieldType.DATE, "{}", "\"2026-03-09\""));
        refused(AttendanceFieldType.DATE, "{}", "\"09.03.2026\"");
        assertEquals("true", kept(AttendanceFieldType.BOOLEAN, "{}", "true"));
        refused(AttendanceFieldType.BOOLEAN, "{}", "\"ja\"");
    }

    @Test
    void aChoiceTakesOnlyItsOptions() {
        assertEquals("\"M\"", kept(AttendanceFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}", "\"M\""));
        refused(AttendanceFieldType.ENUM, "{\"options\":[\"S\",\"M\"]}", "\"XL\"");
    }

    @Test
    void aRequiredFieldMayStayEmpty() {
        assertEquals("\"\"", kept(AttendanceFieldType.STRING, "{\"required\":true}", "\"\""));
    }

    @Test
    void membersAreKeptInEitherShape() {
        assertEquals(String.valueOf(memberId), kept(AttendanceFieldType.MEMBER, "{}", String.valueOf(memberId)));
        assertEquals("\"" + memberId + "\"", kept(AttendanceFieldType.MEMBER, "{}", "\"" + memberId + "\""));
        assertEquals("[" + memberId + "]", kept(AttendanceFieldType.MEMBER_LIST, "{}", "[" + memberId + "]"));
        refused(AttendanceFieldType.MEMBER, "{}", "\"Alma\"");
    }

    @Test
    void aSheetMayNameAMemberOutsideTheFieldsGroup() {
        String config = "{\"groupId\":" + groupId + "}";

        assertEquals(
                String.valueOf(memberId), kept(AttendanceFieldType.MEMBER_OF_GROUP, config, String.valueOf(memberId)));
    }
}
