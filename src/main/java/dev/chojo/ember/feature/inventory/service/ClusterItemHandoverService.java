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
 * What happens to a cluster's gear when a station joins it or leaves it.
 *
 * <p>Joining is the smaller half. A station that already recorded gear as belonging to the body above it was
 * naming a body nobody could ask; now there is one, and every such piece is pointed at it. Nothing else about
 * those items changes, because nothing else about them was wrong.
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
public class ClusterItemHandoverService {
    private static final Logger log = LoggerFactory.getLogger(ClusterItemHandoverService.class);

    private static final String REASON = "The station left the cluster";

    private final InventoryRepository inventoryRepository;
    private final ItemCustodyService custodyService;
    private final ItemMovementService movementService;

    @Inject
    public ClusterItemHandoverService(
            InventoryRepository inventoryRepository,
            ItemCustodyService custodyService,
            ItemMovementService movementService) {
        this.inventoryRepository = inventoryRepository;
        this.custodyService = custodyService;
        this.movementService = movementService;
    }

    /**
     * Points a station's owner-owned gear at the cluster it has just joined.
     *
     * <p>Only gear with no owner named already: a station holding another cluster's items keeps holding them,
     * and the cluster it just joined has no claim on them.
     *
     * @param clusterId the cluster the station has joined
     * @param stationId the station joining it
     * @return how many pieces of gear found their owner
     */
    public int adoptAtStation(int clusterId, int stationId) {
        int adopted = inventoryRepository.adoptClusterItemsAt(clusterId, stationId);
        if (adopted > 0) {
            log.info("Cluster {} took ownership of {} item(s) at station {}", clusterId, adopted, stationId);
        }
        return adopted;
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
