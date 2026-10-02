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
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The association's own member groups over HTTP.
 */
class ClusterMemberRoutesTest extends RepositoryTestBase {
    private RouteHarness harness;
    private int clusterId;

    @BeforeEach
    void freshCluster() {
        harness = RouteHarness.serving(new ClusterMemberRoutes(clusterService, clusterMemberService));
        clusterId = clusterService
                .create("Verband Gruppenrouten " + System.nanoTime(), null)
                .id();
    }

    private int freshMember(int cluster) {
        var account = accountRepo.create("cluster-group-route-" + System.nanoTime() + "@test.com", "Gr", "Uppe");
        return clusterService
                .addMember(cluster, account.id(), ClusterUserType.CLUSTER_USER)
                .id();
    }

    /**
     * One request names the group, says what it carries and who is in it. A refusal for any part leaves
     * all three as they were: a group renamed and granted more while its people stayed the same is a
     * state nobody asked for.
     */
    @Test
    void aCombinedChangeThatIsRefusedLeavesTheGroupAsItWas() {
        int member = freshMember(clusterId);
        int stranger = freshMember(clusterService
                .create("Verband Fremd " + System.nanoTime(), null)
                .id());
        var group = clusterMemberService.createGroup(clusterId, "Vorher");
        clusterMemberService.setGroupMembers(clusterId, group.id(), Set.of(member));

        var answer = harness.request(client -> client.put(
                PREFIX + "/cluster/member-groups/" + group.id(),
                body("{\"name\":\"Nachher\",\"permissions\":[\"CLUSTER_NEWS_EDIT\"],\"memberIds\":[" + member + ","
                        + stranger + "]}"),
                harness.as(TestSessions.clusterMember(clusterId, ClusterPermission.CLUSTER_ADMINISTRATOR))));

        assertEquals(ClusterRefusal.CLUSTER_MEMBER_NOT_HERE, refusalOf(answer));
        var detail = clusterMemberService.findGroupDetail(clusterId, group.id());
        assertEquals("Vorher", detail.group().name());
        assertTrue(detail.permissions().isEmpty());
        assertEquals(List.of(member), detail.memberIds());
    }

    @Test
    void aCombinedChangeWritesAllThreeParts() {
        int member = freshMember(clusterId);
        var group = clusterMemberService.createGroup(clusterId, "Vorher");

        var answer = harness.request(client -> client.put(
                PREFIX + "/cluster/member-groups/" + group.id(),
                body("{\"name\":\" Nachher \",\"permissions\":[\"CLUSTER_NEWS_EDIT\"],\"memberIds\":[" + member + "]}"),
                harness.as(TestSessions.clusterMember(clusterId, ClusterPermission.CLUSTER_ADMINISTRATOR))));

        assertEquals(204, answer.code());
        var detail = clusterMemberService.findGroupDetail(clusterId, group.id());
        assertEquals("Nachher", detail.group().name());
        assertEquals(Set.of(ClusterPermission.CLUSTER_NEWS_EDIT), detail.permissions());
        assertEquals(List.of(member), detail.memberIds());
    }

    @Test
    void aGroupNameAnotherGroupHasIsRefused() {
        clusterMemberService.createGroup(clusterId, "Vorstand");
        var other = clusterMemberService.createGroup(clusterId, "Kasse");

        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.clusterMember(clusterId, ClusterPermission.CLUSTER_ADMINISTRATOR));
            var created = client.post(PREFIX + "/cluster/member-groups", body("{\"name\":\"vorstand \"}"), admin);
            assertEquals(ClusterRefusal.CLUSTER_MEMBER_GROUP_NAME_TAKEN_ON_CREATE, refusalOf(created));
            var renamed =
                    client.put(PREFIX + "/cluster/member-groups/" + other.id(), body("{\"name\":\"VORSTAND\"}"), admin);
            assertEquals(ClusterRefusal.CLUSTER_MEMBER_GROUP_NAME_TAKEN_ON_CHANGE, refusalOf(renamed));
        });
    }
}
