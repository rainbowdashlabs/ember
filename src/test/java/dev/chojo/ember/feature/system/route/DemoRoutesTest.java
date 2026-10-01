/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.service.DemoAccountService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The sign-in page learns what kind of instance it runs on from anybody, and only a demo or development
 * instance lists its accounts.
 */
class DemoRoutesTest {
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-00000000a001");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final StationRepository stations = mock(StationRepository.class);
    private final StationMemberRepository members = mock(StationMemberRepository.class);
    private final MemberGroupRepository groups = mock(MemberGroupRepository.class);
    private final UserTagRepository tags = mock(UserTagRepository.class);
    private final ProfileFieldService profiles = mock(ProfileFieldService.class);
    private final ClusterRepository clusters = mock(ClusterRepository.class);
    private final ClusterService clusterService = mock(ClusterService.class);
    private final DemoAccountService service =
            new DemoAccountService(accounts, stations, members, groups, tags, profiles, clusters, () -> clusterService);

    @Test
    void anybodyLearnsWhatKindOfInstanceThisIs() {
        var response = harness(demo(true, false)).request(client -> client.get(PREFIX + "/demo/status"));

        assertEquals(200, response.code());
        JsonNode status = json(response);
        assertTrue(status.path("demo").asBoolean());
        assertFalse(status.path("dev").asBoolean());
    }

    @Test
    void anInstanceThatIsNeitherDemoNorDevelopmentListsNoAccounts() {
        var response = harness(demo(false, false)).request(client -> client.get(PREFIX + "/demo/accounts"));

        assertEquals(404, response.code());
    }

    @Test
    void theAccountsAreGroupedByStationAndTheRestStandAlone() {
        Station station = mock(Station.class);
        when(station.id()).thenReturn(1);
        when(station.uid()).thenReturn(STATION_UID);
        when(station.name()).thenReturn("North");
        when(stations.findAllRegular()).thenReturn(List.of(station));
        Account member = account(10, "member@example.org", InstanceUserType.USER);
        Account admin = account(11, "admin@example.org", InstanceUserType.ADMINISTRATOR);
        StationMember row = new StationMember(
                100, 1, UUID.randomUUID(), 10, false, null, "Mia", StationUserType.TEAM, LocalDate.EPOCH, null);
        StationMember unclaimed = new StationMember(
                101, 1, UUID.randomUUID(), null, false, null, "Nobody", StationUserType.MEMBER, LocalDate.EPOCH, null);
        when(members.findByStation(1)).thenReturn(List.of(row, unclaimed));
        when(members.findAllByAccountId(10)).thenReturn(List.of(row));
        when(members.findPermissions(100)).thenReturn(List.of(new Permission(1, StationPermission.LOGIN)));
        when(groups.findGroupsForMember(100))
                .thenReturn(List.of(new MemberGroup(1, 1, "Crew", null, 0, null, List.of())));
        when(tags.findTagsForMember(100)).thenReturn(List.of(new UserTag(1, 1, "Driver", null, true, 0)));
        when(profiles.isProfileComplete(100)).thenReturn(true);
        when(accounts.findById(10)).thenReturn(Optional.of(member));
        when(accounts.findAll()).thenReturn(List.of(member, admin));
        Cluster cluster = mock(Cluster.class);
        when(cluster.id()).thenReturn(7);
        when(clusters.findAll()).thenReturn(List.of(cluster));
        ClusterMember clusterAdmin = new ClusterMember(1, 7, 11, ClusterUserType.CLUSTER_ADMIN);
        when(clusterService.findMembers(7)).thenReturn(List.of(clusterAdmin));
        when(clusterService.resolvePermissions(clusterAdmin))
                .thenReturn(Set.of(ClusterPermission.LOGIN, ClusterPermission.CLUSTER_ADMINISTRATOR));

        var response = harness(demo(false, true)).request(client -> client.get(PREFIX + "/demo/accounts"));

        assertEquals(200, response.code());
        JsonNode body = json(response);
        JsonNode group = body.path("stationGroups").get(0);
        assertEquals(STATION_UID.toString(), group.path("stationId").asString());
        assertEquals("North", group.path("stationName").asString());
        JsonNode atStation = group.path("accounts").get(0);
        assertEquals(1, group.path("accounts").size());
        assertEquals("member@example.org", atStation.path("email").asString());
        assertEquals("TEAM", atStation.path("userType").asString());
        assertEquals("LOGIN", atStation.path("permissions").get(0).asString());
        assertEquals("Crew", atStation.path("groups").get(0).asString());
        assertEquals("Driver", atStation.path("tags").get(0).asString());
        assertTrue(atStation.path("profileComplete").asBoolean());
        assertFalse(atStation.path("instanceAdministrator").asBoolean());
        JsonNode alone = body.path("noStationAccounts");
        assertEquals(1, alone.size());
        assertEquals("admin@example.org", alone.get(0).path("email").asString());
        assertEquals("MANAGER", alone.get(0).path("userType").asString());
        assertTrue(alone.get(0).path("instanceAdministrator").asBoolean());
        assertEquals(
                List.of("CLUSTER_ADMINISTRATOR", "LOGIN"),
                alone.get(0)
                        .path("clusterPermissions")
                        .valueStream()
                        .map(JsonNode::asString)
                        .toList());
    }

    private RouteHarness harness(Demo demo) {
        return RouteHarness.serving(new DemoRoutes(demo, service));
    }

    private static Demo demo(boolean enabled, boolean dev) {
        Demo demo = mock(Demo.class);
        when(demo.enabled()).thenReturn(enabled);
        when(demo.dev()).thenReturn(dev);
        return demo;
    }

    private static Account account(int id, String email, InstanceUserType type) {
        return new Account(id, UUID.randomUUID(), email, null, "First", "Last", true, type, null, null, null);
    }
}
