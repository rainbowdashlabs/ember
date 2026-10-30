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
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.cluster.service.ClusterProfileFieldService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.members.entity.FieldSet;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.question.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The association's groups of questions over HTTP, and the questions written with their group and the
 * yes/no question they depend on.
 */
class ClusterFieldSetRoutesTest {
    private static final int CLUSTER_ID = 6;

    private ClusterProfileFieldService fields;
    private ClusterService clusters;

    @BeforeEach
    void setup() {
        clusters = mock(ClusterService.class);
        var cluster = mock(Cluster.class);
        when(cluster.id()).thenReturn(CLUSTER_ID);
        when(clusters.findById(CLUSTER_ID)).thenReturn(Optional.of(cluster));
        fields = mock(ClusterProfileFieldService.class);
    }

    @Test
    void groupsAreListedWrittenRenamedOrderedAndDissolved() {
        var set = new FieldSet(4, CLUSTER_ID, "Atemschutz", true);
        when(fields.findSets(CLUSTER_ID)).thenReturn(List.of(set));
        when(fields.createSet(CLUSTER_ID, "Atemschutz", true)).thenReturn(set);
        var harness = RouteHarness.serving(new ClusterFieldSetRoutes(fields, clusters));
        var editor = harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_FIELD_EDIT));

        harness.run((server, client) -> {
            assertEquals(
                    "Atemschutz",
                    json(client.get(PREFIX + "/cluster/field-sets", editor))
                            .path(0)
                            .path("name")
                            .asString());
            assertEquals(
                    201,
                    client.post(
                                    PREFIX + "/cluster/field-sets",
                                    body("{\"name\": \"Atemschutz\", \"nameAsSection\": true}"),
                                    editor)
                            .code());
            assertEquals(
                    204,
                    client.put(
                                    PREFIX + "/cluster/field-sets/4",
                                    body("{\"name\": \"Neu\", \"nameAsSection\": false}"),
                                    editor)
                            .code());
            assertEquals(
                    204,
                    client.put(PREFIX + "/cluster/field-sets/4/order", body("{\"fieldIds\": [2, 1]}"), editor)
                            .code());
            assertEquals(
                    204,
                    client.put(PREFIX + "/cluster/field-sets/4/order", body("{}"), editor)
                            .code());
            assertEquals(
                    204,
                    client.delete(PREFIX + "/cluster/field-sets/4", null, editor)
                            .code());
        });

        verify(fields).updateSet(CLUSTER_ID, 4, "Neu", false);
        verify(fields).orderSet(CLUSTER_ID, 4, List.of(2, 1));
        verify(fields).orderSet(CLUSTER_ID, 4, List.of());
        verify(fields).dissolveSet(CLUSTER_ID, 4);
    }

    @Test
    void aQuestionIsWrittenWithItsGroupAndTheQuestionItDependsOn() {
        var written = new ClusterProfileField(
                9,
                CLUSTER_ID,
                "G26.3",
                FieldType.DATE,
                ProfileFieldConfig.empty(),
                false,
                false,
                null,
                false,
                false,
                null,
                8,
                4,
                1);
        when(fields.create(
                        anyInt(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        any()))
                .thenReturn(written);
        when(fields.field(CLUSTER_ID, 9)).thenReturn(written);
        var harness = RouteHarness.serving(new ClusterFieldRoutes(clusters, fields));
        var editor = harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_FIELD_EDIT));
        String request = """
                {"name": "G26.3", "fieldType": "DATE", "shownIfFieldId": 8, "fieldSetId": 4,
                 "stationReadonly": false, "keepOnArchive": false}""";

        harness.run((server, client) -> {
            var created = json(client.post(PREFIX + "/cluster/fields", body(request), editor));
            assertEquals(4, created.path("fieldSetId").asInt());
            assertEquals(8, created.path("shownIfFieldId").asInt());
            assertEquals(
                    204,
                    client.put(PREFIX + "/cluster/fields/9", body(request), editor)
                            .code());
        });

        verify(fields, times(2)).requireSetAllowed(CLUSTER_ID, 4);
        verify(fields, times(2)).joinSet(CLUSTER_ID, 9, 4);
        verify(fields, times(2)).dependOn(CLUSTER_ID, 9, 8);
    }
}
