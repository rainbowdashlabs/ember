/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.service.ClusterApplicationService;
import dev.chojo.ember.feature.cluster.service.ClusterAppointmentService;
import dev.chojo.ember.feature.cluster.service.ClusterMemberManagementService;
import dev.chojo.ember.feature.cluster.service.ClusterMemberSearchService;
import dev.chojo.ember.feature.cluster.service.ClusterMemberSearchService.MemberPageResponse;
import dev.chojo.ember.feature.cluster.service.ClusterMemberSearchService.Search;
import dev.chojo.ember.feature.cluster.service.ClusterMemberService;
import dev.chojo.ember.feature.cluster.service.ClusterMemberService.ClusterMemberResponse;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
 * The cluster routes that asked a repository before, over HTTP.
 */
class ClusterRouteLookupsTest {
    private static final int CLUSTER_ID = 5;
    private static final UUID CLUSTER_UID = UUID.fromString("00000000-0000-0000-0001-000000000005");
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private ClusterService clusters;

    @BeforeEach
    void setup() {
        clusters = mock(ClusterService.class);
        var cluster = mock(Cluster.class);
        when(cluster.id()).thenReturn(CLUSTER_ID);
        when(clusters.findById(CLUSTER_ID)).thenReturn(Optional.of(cluster));
    }

    @Test
    void theFirstAdministratorIsAppointedThroughTheAppointmentService() {
        var appointments = mock(ClusterAppointmentService.class);
        var harness = RouteHarness.serving(new ClusterRoutes(clusters, appointments));
        var account = TestSessions.account().uid();

        var answer = harness.request(client -> client.post(
                PREFIX + "/clusters/" + CLUSTER_UID + "/administrators",
                body("{\"accountUid\": \"" + account + "\"}"),
                harness.as(TestSessions.administrator())));

        assertEquals(204, answer.code());
        verify(appointments).appointAdministrator(CLUSTER_UID, account);
    }

    @Test
    void theMembersOfAClusterAreNamedByTheirService() {
        var members = mock(ClusterMemberService.class);
        var member = new ClusterMember(1, CLUSTER_ID, TestSessions.ACCOUNT_ID, ClusterUserType.CLUSTER_ADMIN);
        when(members.findMembers(CLUSTER_ID)).thenReturn(List.of(member));
        when(members.describe(member))
                .thenReturn(new ClusterMemberResponse(1, "uid", "Mara Nager", "mara@test.com", "CLUSTER_ADMIN"));
        var harness = RouteHarness.serving(new ClusterMemberRoutes(clusters, members));

        var answer = harness.request(client -> client.get(
                PREFIX + "/cluster/members",
                harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_MEMBER_READ))));

        assertEquals("Mara Nager", json(answer).get(0).path("name").asString());
    }

    @Test
    void theSearchOfEveryStationGoesThroughTheSearchService() {
        var search = mock(ClusterMemberSearchService.class);
        when(search.search(eq(CLUSTER_ID), any())).thenReturn(new MemberPageResponse(List.of(), 0, 1, 20));
        var harness = RouteHarness.serving(
                new ClusterMemberManagementRoutes(clusters, mock(ClusterMemberManagementService.class), search));

        var answer = harness.request(client -> client.get(
                PREFIX + "/cluster/members/manage/search?q=ma&includeFormer=true&page=1&size=20",
                harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_MEMBER_MANAGER))));

        assertEquals(200, answer.code());
        verify(search).search(CLUSTER_ID, new Search("ma", null, null, true, 1, 20));
    }

    @Test
    void aStationIsLetGoOnlyWhenItIsHere() {
        var stations = mock(StationService.class);
        var station = mock(Station.class);
        when(station.id()).thenReturn(3);
        when(stations.findByUid(STATION_UID)).thenReturn(Optional.of(station));
        var harness = RouteHarness.serving(
                new ClusterStationRoutes(clusters, mock(ClusterApplicationService.class), stations));

        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_STATIONS));
            assertEquals(
                    204,
                    client.delete(PREFIX + "/cluster/stations/" + STATION_UID, null, admin)
                            .code());
            assertEquals(
                    ClusterRefusal.STATION_NOT_HERE_ON_CLUSTER_RELEASE,
                    refusalOf(client.delete(PREFIX + "/cluster/stations/" + UUID.randomUUID(), null, admin)));
        });

        verify(clusters).releaseStation(CLUSTER_ID, 3);
    }
}
