/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldValueEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceSessionField;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What an attendance sheet's own fields take, and how the sheet keeps it.
 *
 * <p>First written down before the field types were brought together. A number on a sheet is whole
 * now, a member outside the group a field is narrowed to is refused, and every answer is kept in the
 * one shape its type is kept in, with nothing kept for a blank one.
 */
class AttendanceAnswersTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static AttendanceService service;
    private static Station station;
    private static int memberId;
    private static int groupMemberId;
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
                new AttendanceAudienceService(attendanceRepo),
                memberEligibility);
        station = stationRepo.create("AttendanceAnswersStation");
        memberId = member("attendance-answers@test.com", "Alma");
        groupMemberId = member("attendance-answers-group@test.com", "Berta");
        groupId = memberGroupRepo.create(station.id(), "Niemand").id();
        memberGroupRepo.addMember(groupId, groupMemberId);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static int member(String email, String firstName) {
        return stationMemberRepo
                .create(station.id(), accountRepo.create(email, firstName, "A").id())
                .id();
    }

    /** A fresh sheet whose only field is of the given type, and that field. */
    private record Sheet(int sessionId, int fieldId) {}

    private static Sheet sheet(FieldType type, String config) {
        var template = service.createTemplate(station.id(), "Bogen " + NAMES.incrementAndGet());
        var field = service.createTemplateField(
                        template.id(), type.name(), type, AttendanceFieldConfig.parse(config), 0)
                .getFirst();
        var start = Instant.now().plus(1, ChronoUnit.DAYS);
        var session = service.createSession(template.id(), start, start.plus(2, ChronoUnit.HOURS), null, null, null);
        return new Sheet(session.id(), field.id());
    }

    private static @Nullable String held(Sheet sheet, List<AttendanceSessionField> fields) {
        return fields.stream()
                .filter(field -> field.fieldId() == sheet.fieldId())
                .map(AttendanceSessionField::value)
                .findFirst()
                .orElse(null);
    }

    /** Writes one answer into a fresh sheet whose only field is of the given type, and reads it back. */
    private static @Nullable String kept(FieldType type, String config, String answer) {
        var sheet = sheet(type, config);
        return held(
                sheet,
                service.setSessionFields(
                        sheet.sessionId(), List.of(new AttendanceFieldValueEntry(sheet.fieldId(), answer))));
    }

    private static void refused(FieldType type, String config, String answer) {
        assertThrows(RefusalResponse.class, () -> kept(type, config, answer), answer + " under " + type);
    }

    @Test
    void aNumberIsWhole() {
        assertEquals("3", kept(FieldType.NUMBER, "{}", "3"));
        refused(FieldType.NUMBER, "{}", "2.5");
        refused(FieldType.NUMBER, "{}", "\"zwei\"");
    }

    @Test
    void aDayAndAYesOrNoAreMeasuredAndKeptInTheirShape() {
        assertEquals("\"2026-03-09\"", kept(FieldType.DATE, "{}", "\"2026-03-09\""));
        refused(FieldType.DATE, "{}", "\"09.03.2026\"");
        assertEquals("true", kept(FieldType.BOOLEAN, "{}", "true"));
        assertEquals("true", kept(FieldType.BOOLEAN, "{}", "\"1\""));
        refused(FieldType.BOOLEAN, "{}", "\"ja\"");
    }

    @Test
    void aChoiceTakesOnlyItsOptions() {
        assertEquals("\"M\"", kept(FieldType.CHOICE, "{\"options\":[\"S\",\"M\"]}", "\"M\""));
        refused(FieldType.CHOICE, "{\"options\":[\"S\",\"M\"]}", "\"XL\"");
    }

    @Test
    void aRequiredFieldMayStayEmptyAndKeepsNothing() {
        assertNull(kept(FieldType.TEXT, "{\"required\":true}", "\"\""));
    }

    @Test
    void aClearedFieldKeepsNothing() {
        var sheet = sheet(FieldType.TEXT, "{}");
        service.setSessionFields(sheet.sessionId(), List.of(new AttendanceFieldValueEntry(sheet.fieldId(), "\"x\"")));

        var fields = service.setSessionFields(
                sheet.sessionId(), List.of(new AttendanceFieldValueEntry(sheet.fieldId(), "\"\"")));

        assertNull(held(sheet, fields));
    }

    @Test
    void membersAreKeptAsNumbersWhicheverShapeTheyArriveIn() {
        assertEquals(String.valueOf(memberId), kept(FieldType.MEMBER, "{}", String.valueOf(memberId)));
        assertEquals(String.valueOf(memberId), kept(FieldType.MEMBER, "{}", "\"" + memberId + "\""));
        assertEquals("[" + memberId + "]", kept(FieldType.MEMBER_LIST, "{}", "[" + memberId + "]"));
        refused(FieldType.MEMBER, "{}", "\"Alma\"");
    }

    @Test
    void aSheetRefusesAMemberOutsideTheFieldsGroup() {
        String config = "{\"groupId\":" + groupId + "}";

        assertEquals(
                String.valueOf(groupMemberId), kept(FieldType.MEMBER_OF_GROUP, config, String.valueOf(groupMemberId)));
        refused(FieldType.MEMBER_OF_GROUP, config, String.valueOf(memberId));
        refused(FieldType.MEMBER_LIST_OF_GROUP, config, "[" + groupMemberId + "," + memberId + "]");
    }

    @Test
    void aDateFieldMayStartAtToday() {
        var sheet = sheet(FieldType.DATE, "{\"defaultValue\":\"__TODAY__\"}");

        assertEquals("\"" + LocalDate.now() + "\"", held(sheet, attendanceRepo.findSessionFields(sheet.sessionId())));
    }
}
