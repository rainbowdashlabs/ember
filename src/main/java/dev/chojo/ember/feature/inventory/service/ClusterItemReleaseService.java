/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * What happens to a cluster's gear when one of its stations leaves.
 *
 * <p>The rows are not deleted. A station losing its cluster is not the same as the gear ceasing to exist, and
 * deleting it would take the cluster's own record of what it owns with it. Instead everything the cluster owns
 * and that station held goes back into the cluster's store: whoever had it stops having it, whatever container
 * it sat in is cleared, and any chain it was walking is called off, because one end of that chain has just
 * stopped being reachable.
 *
 * <p>Its own service rather than a method on the cluster, because it needs both the movement side and the
 * custody side of the inventory and the cluster has no business knowing either.
 */
@Singleton
public class ClusterItemReleaseService {
    private static final Logger log = LoggerFactory.getLogger(ClusterItemReleaseService.class);

    private static final String REASON = "The station left the cluster";

    private final InventoryRepository inventoryRepository;
    private final ItemCustodyService custodyService;
    private final ItemMovementService movementService;

    @Inject
    public ClusterItemReleaseService(
            InventoryRepository inventoryRepository,
            ItemCustodyService custodyService,
            ItemMovementService movementService) {
        this.inventoryRepository = inventoryRepository;
        this.custodyService = custodyService;
        this.movementService = movementService;
    }

    /**
     * Brings a cluster's gear home from a station that is leaving it.
     *
     * @param clusterId the cluster
     * @param stationId the station being released
     * @return how many items came back
     */
    public int recallFromStation(int clusterId, int stationId) {
        List<InventoryItem> held = inventoryRepository.findClusterItemsHeldBy(clusterId, stationId);
        for (InventoryItem item : held) {
            if (item.custody() == ItemCustody.IN_TRANSIT && item.custodyMovementId() != null) {
                movementService.abandon(item.custodyMovementId(), REASON);
            }
            custodyService.returnToOwner(item.id());
        }
        if (!held.isEmpty()) {
            log.info("Recalled {} item(s) of cluster {} from station {}", held.size(), clusterId, stationId);
        }
        return held.size();
    }
}
