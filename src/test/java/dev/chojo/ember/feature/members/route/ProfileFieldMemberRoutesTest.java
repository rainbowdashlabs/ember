/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The answers of one member are only read for a member of the caller's station, and only read and
 * written by the member themselves, their guardian or somebody the station lets read or edit members.
 */
class ProfileFieldMemberRoutesTest {
    private static final int STATION = 3;
    private static final int OTHER_MEMBER = 7;
    private static final int FOREIGN_MEMBER = 8;
    private static final int WARD = 9;

    private final StationMemberService members = mock(StationMemberService.class);
    private final StationMemberRepository memberRepository = mock(StationMemberRepository.class);
    private final ProfileFieldService fields = mock(ProfileFieldService.class);
    private final RouteHarness harness =
            RouteHarness.serving(new ProfileFieldRoutes(fields, members, new GuardianPolicy(memberRepository)));

    ProfileFieldMemberRoutesTest() {
        when(members.findById(OTHER_MEMBER)).thenReturn(Optional.of(member(OTHER_MEMBER, STATION)));
        when(members.findById(FOREIGN_MEMBER)).thenReturn(Optional.of(member(FOREIGN_MEMBER, 99)));
        when(members.findById(WARD)).thenReturn(Optional.of(member(WARD, STATION)));
        when(members.findById(TestSessions.MEMBER_ID)).thenReturn(Optional.of(member(TestSessions.MEMBER_ID, STATION)));
        when(memberRepository.findManaged(TestSessions.MEMBER_ID)).thenReturn(List.of(member(WARD, STATION)));
    }

    private static StationMember member(int id, int stationId) {
        return new StationMember(id, stationId, null, 1, false, null, "Mara", StationUserType.MEMBER, null);
    }

    private int read(UserSession session, int memberId) {
        var answered = harness.request(
                client -> client.get(PREFIX + "/station-members/" + memberId + "/profile", harness.as(session)));
        return answered.code();
    }

    private int write(UserSession session, int memberId) {
        var answered = harness.request(client -> client.put(
                PREFIX + "/station-members/" + memberId + "/profile", body("{\"values\": []}"), harness.as(session)));
        return answered.code();
    }

    @Test
    void aMembersAnswersAndQuestionsAreReadAtTheirStationOnly() {
        var session = TestSessions.member(STATION, StationPermission.USER, StationPermission.MEMBER_READ);

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.get(PREFIX + "/station-members/7/profile", harness.as(session))
                            .code());
            assertEquals(
                    200,
                    client.get(PREFIX + "/station-members/7/fields", harness.as(session))
                            .code());
            assertEquals(
                    GeneralRefusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.get(PREFIX + "/station-members/8/profile", harness.as(session))));
        });

        verify(fields).findValues(OTHER_MEMBER);
    }

    @Test
    void aPlainMemberMayNotReadAnotherMembersAnswers() {
        var session = TestSessions.member(STATION, StationPermission.USER);

        var answered = harness.request(
                client -> client.get(PREFIX + "/station-members/" + OTHER_MEMBER + "/profile", harness.as(session)));

        assertEquals(MemberRefusal.PROFILE_NOT_YOURS_TO_READ, refusalOf(answered));
        verify(fields, never()).findValues(anyInt());
    }

    @Test
    void aPlainMemberMayNotWriteAnotherMembersAnswers() {
        var session = TestSessions.member(STATION, StationPermission.USER);

        var answered = harness.request(client -> client.put(
                PREFIX + "/station-members/" + OTHER_MEMBER + "/profile",
                body("{\"values\": []}"),
                harness.as(session)));

        assertEquals(MemberRefusal.PROFILE_NOT_YOURS_TO_WRITE, refusalOf(answered));
        verify(fields, never()).setValues(anyInt(), anyList(), anyInt());
    }

    @Test
    void aMemberWhoMayOnlyReadMembersMayNotWriteAnotherMembersAnswers() {
        var session = TestSessions.member(STATION, StationPermission.USER, StationPermission.MEMBER_READ);

        assertEquals(403, write(session, OTHER_MEMBER));
    }

    @Test
    void aMemberReadsAndWritesTheirOwnAnswers() {
        var session = TestSessions.member(STATION, StationPermission.USER);

        assertEquals(200, read(session, TestSessions.MEMBER_ID));
        assertEquals(200, write(session, TestSessions.MEMBER_ID));
    }

    @Test
    void aGuardianReadsAndWritesTheirWardsAnswers() {
        var session = TestSessions.member(STATION, StationPermission.USER);

        assertEquals(200, read(session, WARD));
        assertEquals(200, write(session, WARD));
    }

    @Test
    void aMemberEditorReadsAndWritesAnotherMembersAnswers() {
        var session = TestSessions.member(
                STATION, StationPermission.USER, StationPermission.MEMBER_READ, StationPermission.MEMBER_EDIT);

        assertEquals(200, read(session, OTHER_MEMBER));
        assertEquals(200, write(session, OTHER_MEMBER));
    }
}
