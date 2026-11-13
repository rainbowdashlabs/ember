/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.route.EventGroupRoutes.EventGroupSummary;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupDetail;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService.PartnerStanding;
import dev.chojo.ember.feature.events.service.PartnerEventGroupService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedEventGroupRoutesTest {
    private static final int STATION_ID = 3;
    private static final int CHILD_ID = 12;
    private static final UUID HOLDER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID CHILD_UID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final String EVENT_GROUP = PREFIX + "/federated/" + HOLDER + "/events/groups/70";

    private PartnerEventGroupService partnerEventGroups;
    private GuardianPolicy guardians;
    private RouteHarness harness;

    private static StationMember member(int id, UUID uid) {
        return new StationMember(
                id, STATION_ID, uid, null, false, null, "Kind", StationUserType.MEMBER, LocalDate.of(2020, 1, 1), null);
    }

    @BeforeEach
    void serve() {
        partnerEventGroups = mock(PartnerEventGroupService.class);
        guardians = mock(GuardianPolicy.class);
        var members = mock(StationMemberService.class);
        var self = TestSessions.member(STATION_ID).member();
        when(members.findById(TestSessions.MEMBER_ID)).thenReturn(Optional.of(self));
        when(members.findById(CHILD_ID)).thenReturn(Optional.of(member(CHILD_ID, CHILD_UID)));
        when(members.findManaged(TestSessions.MEMBER_ID)).thenReturn(List.of(member(CHILD_ID, CHILD_UID)));
        var identities = mock(MemberIdentityFactory.class);
        when(identities.local(anyInt(), anyInt()))
                .thenAnswer(call -> new MemberIdentity(UUID.randomUUID(), UUID.randomUUID()));
        var standing =
                new PartnerStanding(CHILD_UID, RegistrationStatus.ACCEPTED, Instant.now(), List.of(101), List.of());
        when(partnerEventGroups.signUp(anyInt(), any(), anyInt(), any(), any())).thenReturn(standing);
        var eventGroup = new EventGroup(
                70,
                9,
                "Jugendflamme 1",
                null,
                null,
                null,
                false,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null);
        when(partnerEventGroups.eventGroup(anyInt(), eq(HOLDER), eq(70), any()))
                .thenReturn(new RemoteEventGroupDetail(
                        EventGroupSummary.of(eventGroup, 1, 1),
                        List.of(),
                        List.of(),
                        List.of(standing, new PartnerStanding(UUID.randomUUID(), null, null, List.of(), List.of())),
                        null));
        harness =
                RouteHarness.serving(new FederatedEventGroupRoutes(partnerEventGroups, members, guardians, identities));
    }

    private Response call(String method, String path, String json) {
        var session = harness.as(TestSessions.member(STATION_ID, StationPermission.USER));
        return harness.request(client -> switch (method) {
            case "GET" -> client.get(path, session);
            default -> client.post(path, body(json), session);
        });
    }

    @Test
    void theEventGroupShowsOnlyTheReadersHousehold() {
        var opened = json(call("GET", EVENT_GROUP, null));

        assertEquals(1, opened.get("household").size());
        assertEquals(CHILD_ID, opened.get("household").get(0).get("memberId").asInt());
        verify(partnerEventGroups)
                .eventGroup(
                        STATION_ID,
                        HOLDER,
                        70,
                        Map.of(
                                TestSessions.member(STATION_ID).member().uid(),
                                TestSessions.MEMBER_ID,
                                CHILD_UID,
                                CHILD_ID));
    }

    @Test
    void aGuardianSignsUpTheirChildAndAStrangerIsRefused() {
        when(guardians.mayActFor(any(), eq(CHILD_ID))).thenReturn(true);
        assertEquals(
                201,
                call("POST", EVENT_GROUP + "/sign-up", "{\"memberId\": 12, \"parts\": [101]}")
                        .code());
        verify(partnerEventGroups).signUp(eq(STATION_ID), eq(HOLDER), eq(70), any(), eq(Set.of(101)));

        assertEquals(
                EventRefusal.MEMBER_NOT_YOURS_TO_REGISTER,
                refusalOf(call("POST", EVENT_GROUP + "/sign-up", "{\"memberId\": 99, \"parts\": [101]}")));
        verify(partnerEventGroups, never()).signUp(anyInt(), any(), anyInt(), eq(member(99, CHILD_UID)), any());
    }
}
