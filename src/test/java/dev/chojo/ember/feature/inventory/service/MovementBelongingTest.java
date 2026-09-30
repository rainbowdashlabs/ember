/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.MovementTargeting.Target;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Where a movement belongs when the combination it was started on has since been unbound, and the
 * body that owns its gear.
 */
class MovementBelongingTest {
    private ClusterRepository clusters;
    private MovementFlowService flows;
    private MovementTargeting targeting;

    @BeforeEach
    void setup() {
        clusters = mock(ClusterRepository.class);
        flows = mock(MovementFlowService.class);
        targeting = new MovementTargeting(mock(InventoryRepository.class), clusters, flows);
    }

    @Test
    void aMovementWhoseCombinationIsBoundBelongsOnThatChain() {
        when(flows.resolveFlow(anyInt(), any(), any(), any(), any(), any())).thenReturn(6);

        var target = targeting.belongsOn(MovementGuardsTest.movement(1, 3, 11, null));

        assertEquals(6, target.flowId());
        assertEquals(MovementParty.MEMBER, target.party());
    }

    @Test
    void anUnboundCombinationFallsBackToTheChainItWalks() {
        when(flows.resolveFlow(anyInt(), any(), any(), any(), any(), any()))
                .thenThrow(new BadRequestResponse("no chain"));

        var target = targeting.belongsOn(MovementGuardsTest.movement(1, 3, null, null));

        assertEquals(1, target.flowId());
        assertEquals(MovementParty.STORE, target.party());
    }

    @Test
    void onlyGearOfAClusterHereNamesItsOwner() {
        var cluster = mock(Cluster.class);
        when(clusters.findById(7)).thenReturn(Optional.of(cluster));

        assertSame(
                cluster,
                targeting
                        .owningCluster(new Target(ItemOwner.CLUSTER, 7, MovementParty.MEMBER, 1))
                        .orElseThrow());
        assertTrue(targeting
                .owningCluster(new Target(ItemOwner.CLUSTER, null, MovementParty.MEMBER, 1))
                .isEmpty());
        assertTrue(targeting
                .owningCluster(new Target(ItemOwner.STATION, 7, MovementParty.MEMBER, 1))
                .isEmpty());
    }
}
