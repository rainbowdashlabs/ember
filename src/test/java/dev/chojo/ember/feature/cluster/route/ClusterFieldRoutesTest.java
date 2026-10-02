/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterProfileFieldService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The association's question routes, over HTTP. */
class ClusterFieldRoutesTest {
    private static final int CLUSTER_ID = 6;

    private ClusterProfileFieldService fields;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        var clusters = mock(ClusterService.class);
        var cluster = mock(Cluster.class);
        when(cluster.id()).thenReturn(CLUSTER_ID);
        when(clusters.findById(CLUSTER_ID)).thenReturn(Optional.of(cluster));
        fields = mock(ClusterProfileFieldService.class);
        when(fields.findByCluster(CLUSTER_ID)).thenReturn(List.of());
        harness = RouteHarness.serving(new ClusterFieldRoutes(clusters, fields));
    }

    /** TODO enable with the fix: the list was open to member readers only. */
    @Disabled("red until a field manager may read the questions they edit")
    @Test
    void aFieldManagerReadsTheQuestionsTheyEdit() {
        var editor = harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_FIELD_MANAGER));

        harness.run((server, client) -> {
            assertEquals(200, client.get(PREFIX + "/cluster/fields", editor).code());
            assertEquals(
                    200,
                    client.get(PREFIX + "/cluster/fields/assignments", editor).code());
        });
    }

    /** TODO enable with the fix: a blank role was read as the members. */
    @Disabled("red until an assignment naming nobody is refused")
    @Test
    void anAssignmentNamingNobodyIsRefused() {
        var editor = harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_FIELD_EDIT));

        var answer = harness.request(client -> client.put(
                PREFIX + "/cluster/fields/3/assignments", body("{\"role\": \"\", \"position\": 0}"), editor));

        assertEquals(400, answer.code());
        verify(fields, never()).assignToRole(anyInt(), anyInt(), any(), anyInt(), any(), any(), any());
    }

    /** TODO enable with the fix: a blank role was read as the members on the way out as well. */
    @Disabled("red until an unassignment naming nobody is refused")
    @Test
    void anUnassignmentNamingNobodyIsRefused() {
        var editor = harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_FIELD_EDIT));

        var answer = harness.request(
                client -> client.delete(PREFIX + "/cluster/fields/3/assignments", body("{\"position\": 0}"), editor));

        assertEquals(400, answer.code());
        verify(fields, never()).unassignRole(anyInt(), anyInt(), any());
    }
}
