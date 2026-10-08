/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.MemberWithName;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.GroupMembershipService;
import dev.chojo.ember.feature.members.service.GroupRulesService;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberViewService;
import dev.chojo.ember.feature.members.service.PrivateTags;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.members.service.UserTypeChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The members of a group and of a tag, and the groups and tags of a member, named through the
 * member views and only for members of the caller's station.
 */
class MemberGroupingRoutesTest {
    private static final int STATION_ID = 3;

    private StationMemberService memberService;
    private MemberViewService memberViews;
    private MemberGroupService groups;
    private GroupMembershipService memberships;
    private UserTagService tags;
    private RouteHarness harness;

    private static StationMember member(int stationId) {
        return new StationMember(7, stationId, null, 1, false, null, "Mara", StationUserType.MEMBER, null);
    }

    @BeforeEach
    void setup() {
        memberService = mock(StationMemberService.class);
        memberViews = mock(MemberViewService.class);
        groups = mock(MemberGroupService.class);
        tags = mock(UserTagService.class);
        when(memberService.findById(7)).thenReturn(Optional.of(member(STATION_ID)));
        when(memberService.findById(8)).thenReturn(Optional.of(member(99)));
        when(memberViews.named(any()))
                .thenReturn(new MemberWithName(
                        7,
                        STATION_ID,
                        1,
                        "Mara",
                        "Mara",
                        "N",
                        null,
                        "m@test.com",
                        null,
                        StationUserType.MEMBER,
                        true,
                        null,
                        null,
                        null));
        var jugend = new MemberGroup(4, STATION_ID, "Jugend", null, 0, null, List.of());
        when(groups.findById(4)).thenReturn(Optional.of(jugend));
        when(groups.findMembers(4)).thenReturn(List.of(member(STATION_ID)));
        memberships = mock(GroupMembershipService.class);
        when(memberships.setMembers(eq(jugend), eq(List.of(7)), eq(false), any()))
                .thenReturn(List.of(member(STATION_ID)));
        when(tags.findById(5))
                .thenReturn(Optional.of(new UserTag(5, STATION_ID, "Atemschutz", null, TagVisibility.BADGE, 0)));
        when(tags.findMembers(5)).thenReturn(List.of(member(STATION_ID)));
        var privateTags = mock(PrivateTags.class);
        when(privateTags.visibleTo(any(), any(UserTag.class))).thenReturn(true);
        harness = RouteHarness.serving(
                new MemberGroupRoutes(
                        groups,
                        memberships,
                        memberService,
                        memberViews,
                        mock(UserTypeChangeService.class),
                        mock(GroupRulesService.class)),
                new UserTagRoutes(tags, memberService, memberViews, privateTags));
    }

    @Test
    void groupMembersAreNamedAndSet() {
        var session =
                TestSessions.member(STATION_ID, StationPermission.MEMBER_MANAGE_GROUP, StationPermission.MEMBER_READ);

        harness.run((server, client) -> {
            assertEquals(
                    "Mara",
                    json(client.get(PREFIX + "/groups/4/members", harness.as(session)))
                            .get(0)
                            .path("name")
                            .asString());
            var set = client.put(PREFIX + "/groups/4/members", body("{\"memberIds\": [7]}"), harness.as(session));
            assertEquals(1, json(set).size());
            assertEquals(
                    200,
                    client.get(PREFIX + "/station-members/7/groups", harness.as(session))
                            .code());
            assertEquals(
                    GeneralRefusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.get(PREFIX + "/station-members/8/groups", harness.as(session))));
        });

        verify(groups).findGroupsForMember(7);
    }

    @Test
    void tagMembersAreNamedAndSet() {
        var session =
                TestSessions.member(STATION_ID, StationPermission.MEMBER_MANAGE_TAGS, StationPermission.MEMBER_READ);

        harness.run((server, client) -> {
            assertEquals(
                    "Mara",
                    json(client.get(PREFIX + "/tags/5/members", harness.as(session)))
                            .get(0)
                            .path("name")
                            .asString());
            var set = client.put(PREFIX + "/tags/5/members", body("{\"memberIds\": null}"), harness.as(session));
            assertEquals(1, json(set).size());
            assertEquals(
                    200,
                    client.get(PREFIX + "/station-members/7/tags", harness.as(session))
                            .code());
            assertEquals(
                    GeneralRefusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.get(PREFIX + "/station-members/8/tags", harness.as(session))));
        });

        verify(tags).setMembers(5, List.of());
        verify(tags).findTagsForMember(7);
    }
}
