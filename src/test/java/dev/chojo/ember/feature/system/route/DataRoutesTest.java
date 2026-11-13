/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.service.PermissionHierarchyService;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The permission hierarchies the picker draws, without the permissions of a switched-off module. */
class DataRoutesTest {
    private static final int STATION_ID = 7;

    private final ModuleAvailability modules = mock(ModuleAvailability.class);
    private final RouteHarness harness = RouteHarness.serving(new DataRoutes(new PermissionHierarchyService(modules)));

    /** Asked without a station, every permission there is comes back. */
    @Test
    void withoutAStationEveryPermissionIsListed() {
        var nodes = json(harness.request(client -> client.get(PREFIX + "/data/permissions")));

        assertEquals(StationPermission.values().length, nodes.size());
    }

    /** Asked for a station, the permissions of what it switched off are gone, as children too. */
    @Test
    void aStationsSwitchedOffModulesAreLeftOut() {
        when(modules.disabledAt(STATION_ID))
                .thenReturn(StationModule.withChildren(List.of(StationModule.INVENTORY_LENDING, StationModule.NEWS)));
        var session = harness.as(TestSessions.member(STATION_ID, StationPermission.LOGIN));

        var nodes = json(harness.request(client -> client.get(PREFIX + "/data/permissions", session)));

        List<String> names = new ArrayList<>();
        nodes.forEach(node -> names.add(node.path("name").asString()));
        assertFalse(names.contains("INVENTORY_LENDING_MANAGER"));
        assertFalse(names.contains("NEWS_MANAGER"));
        assertTrue(names.contains("INVENTORY_MANAGER"));
        JsonNode manager = find(nodes, "INVENTORY_MANAGER");
        List<String> children = new ArrayList<>();
        manager.path("children").forEach(child -> children.add(child.asString()));
        assertFalse(children.contains("INVENTORY_LENDING_MANAGER"));
        assertTrue(children.contains("INVENTORY_MOVEMENTS"));
    }

    /** The cluster hierarchy lists every cluster permission. */
    @Test
    void theClusterHierarchyIsComplete() {
        var nodes = json(harness.request(client -> client.get(PREFIX + "/data/cluster-permissions")));

        assertEquals(ClusterPermission.values().length, nodes.size());
    }

    private static JsonNode find(JsonNode nodes, String name) {
        for (JsonNode node : nodes) {
            if (node.path("name").asString().equals(name)) return node;
        }
        throw new AssertionError(name + " missing");
    }
}
