/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The answers of one member are only read for a member of the caller's station.
 */
class ProfileFieldMemberRoutesTest {

    private static StationMember member(int stationId) {
        return new StationMember(7, stationId, null, 1, false, null, "Mara", StationUserType.MEMBER, null);
    }

    @Test
    void aMembersAnswersAndQuestionsAreReadAtTheirStationOnly() {
        var members = mock(StationMemberService.class);
        var fields = mock(ProfileFieldService.class);
        when(members.findById(7)).thenReturn(Optional.of(member(3)));
        when(members.findById(8)).thenReturn(Optional.of(member(99)));
        when(fields.findValues(7)).thenReturn(List.of());
        when(fields.findApplicableFields(7)).thenReturn(List.of());
        var harness = RouteHarness.serving(new ProfileFieldRoutes(fields, members));
        var session = TestSessions.member(3, StationPermission.USER);

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
                    Refusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.get(PREFIX + "/station-members/8/profile", harness.as(session))));
        });

        verify(fields).findValues(7);
    }
}
