/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.feature.attendance.service.AttendanceExportService;
import dev.chojo.ember.feature.attendance.service.AttendanceReportService;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.attendance.service.MemberCheckNotesService;
import dev.chojo.ember.feature.members.entity.MemberAbsence;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Absences over HTTP: a member of another station is not here, and whoever recorded an absence is
 * named the way the station calls them.
 */
class AttendanceAbsenceRoutesTest {
    private static final int STATION = 3;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    private AttendanceService attendance;
    private StationMemberService members;
    private MemberNameResolver names;
    private RouteHarness harness;

    private static StationMember member(int id, int stationId) {
        return new StationMember(id, stationId, null, null, false, null, "m", StationUserType.MEMBER, null);
    }

    @BeforeEach
    void setup() {
        attendance = mock(AttendanceService.class);
        members = mock(StationMemberService.class);
        names = mock(MemberNameResolver.class);
        when(members.findById(anyInt())).thenReturn(Optional.empty());
        when(members.findById(5)).thenReturn(Optional.of(member(5, STATION)));
        when(members.findById(6)).thenReturn(Optional.of(member(6, 9)));
        harness = RouteHarness.serving(new AttendanceRoutes(
                attendance,
                mock(MemberCheckNotesService.class),
                mock(AttendanceExportService.class),
                mock(AttendanceReportService.class),
                members,
                names,
                mock(MemberIdentityFactory.class)));
    }

    @Test
    void aMembersAbsencesAreListedOnlyForAMemberOfTheStation() {
        when(attendance.findAbsencesByMember(5)).thenReturn(List.of());

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(STATION, StationPermission.ATTENDANCE_EDIT));
            assertEquals(
                    200,
                    client.get(PREFIX + "/attendance/absences/member/5", editor).code());
            assertEquals(
                    AttendanceRefusal.ATTENDANCE_MEMBER_NOT_HERE,
                    refusalOf(client.get(PREFIX + "/attendance/absences/member/6", editor)));
            assertEquals(
                    AttendanceRefusal.ATTENDANCE_MEMBER_NOT_HERE,
                    refusalOf(client.get(PREFIX + "/attendance/absences/member/7", editor)));
        });

        verify(attendance, never()).findAbsencesByMember(6);
    }

    @Test
    void anAbsenceOfAnotherStationsMemberIsNotDeleted() {
        when(attendance.findAbsenceById(1))
                .thenReturn(Optional.of(new MemberAbsence(1, 6, DAY, DAY, null, null, null)));

        var answer = harness.request(client -> client.delete(
                PREFIX + "/attendance/absences/1",
                null,
                harness.as(TestSessions.member(STATION, StationPermission.ATTENDANCE_MANAGER))));

        assertEquals(AttendanceRefusal.ATTENDANCE_MEMBER_NOT_HERE, refusalOf(answer));
        verify(attendance, never()).deleteAbsence(1);
    }

    @Test
    void whoRecordedAnAbsenceIsNamedAsTheStationCallsThem() {
        when(attendance.findAbsencesByMember(TestSessions.MEMBER_ID))
                .thenReturn(List.of(
                        new MemberAbsence(1, TestSessions.MEMBER_ID, DAY, DAY, "Urlaub", null, 5),
                        new MemberAbsence(2, TestSessions.MEMBER_ID, DAY, DAY, null, null, null)));
        when(names.called(5)).thenReturn("Kim");

        var answer = harness.request(client -> client.get(
                PREFIX + "/profile/absences", harness.as(TestSessions.member(STATION, StationPermission.USER))));

        var absences = json(answer);
        assertEquals("Kim", absences.get(0).path("createdByName").asString());
        assertFalse(absences.get(1).path("createdByName").isString());
    }
}
