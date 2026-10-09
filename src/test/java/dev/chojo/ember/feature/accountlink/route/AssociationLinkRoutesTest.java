/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.feature.accountlink.service.TestAccountLinks;
import dev.chojo.ember.feature.cluster.route.ClusterMemberRoutes;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An association adding an address that already has an account, and its requests over HTTP.
 */
class AssociationLinkRoutesTest extends RepositoryTestBase {
    private RouteHarness harness;
    private int clusterId;

    @BeforeEach
    void setup() {
        harness = RouteHarness.serving(
                new ClusterMemberRoutes(clusterService, clusterMemberService),
                new AssociationLinkRoutes(
                        TestAccountLinks.associationService(accountRepo, clusterRepo), clusterService));
        clusterId = clusterService
                .create("Verband Anfragerouten " + System.nanoTime(), null)
                .id();
    }

    @Test
    void addingAKnownAddressAnswersWithTheRequestAndTheListShowsIt() {
        var known = accountRepo.create("association-route-" + System.nanoTime() + "@test.com", "Kai", "Bekannt");

        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.clusterMember(
                    clusterId, ClusterPermission.CLUSTER_ADMINISTRATOR, ClusterPermission.CLUSTER_MEMBER_READ));
            var added = client.post(
                    PREFIX + "/cluster/members",
                    body("{\"email\":\"" + known.email() + "\",\"userType\":\"CLUSTER_USER\"}"),
                    admin);
            assertEquals(202, added.code());
            var request = json(added);
            assertEquals("WAITING", request.get("status").asText());
            assertTrue(clusterRepo.findMember(clusterId, known.id()).isEmpty());

            var listed = json(client.get(PREFIX + "/cluster/link-requests", admin));
            assertEquals(1, listed.size());
            assertEquals(known.email(), listed.get(0).get("address").asText());

            var again = client.post(
                    PREFIX + "/cluster/link-requests/" + request.get("uid").asText() + "/send-again",
                    body("{}"),
                    admin);
            assertEquals(ClusterRefusal.CLUSTER_LINK_SENT_TOO_RECENTLY, refusalOf(again));
        });
    }

    @Test
    void anotherAssociationSeesNothingOfTheRequests() {
        var known = accountRepo.create("association-route-other-" + System.nanoTime() + "@test.com", "Ole", "Fremd");
        int otherCluster = clusterService
                .create("Verband Andere Routen " + System.nanoTime(), null)
                .id();
        var request = TestAccountLinks.associationService(accountRepo, clusterRepo)
                .ask(clusterId, known.id(), ClusterUserType.CLUSTER_USER, known.email());

        harness.run((server, client) -> {
            var other = harness.as(TestSessions.clusterMember(
                    otherCluster, ClusterPermission.CLUSTER_ADMINISTRATOR, ClusterPermission.CLUSTER_MEMBER_READ));
            assertEquals(
                    0,
                    json(client.get(PREFIX + "/cluster/link-requests", other)).size());
            var again =
                    client.post(PREFIX + "/cluster/link-requests/" + request.uid() + "/send-again", body("{}"), other);
            assertEquals(ClusterRefusal.CLUSTER_LINK_REQUEST_NOT_HERE, refusalOf(again));
        });
    }
}
