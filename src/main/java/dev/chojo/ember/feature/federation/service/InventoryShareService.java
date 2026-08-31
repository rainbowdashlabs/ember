/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.InventoryShare;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.SharePolicy;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.InventoryShareRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a station offers its lending partners, and the answer to "is this piece of gear on offer to
 * that station".
 *
 * <p>Sharing is opt-in: gear nobody has said anything about is not offered. The resolution runs on
 * the owning station's rows, never the asking station's, because a predicate the asking side applies
 * is a predicate the asking side can decline to apply.
 */
@Singleton
public class InventoryShareService {
    private static final Logger log = LoggerFactory.getLogger(InventoryShareService.class);

    private final InventoryShareRepository repository;
    private final FederationService federationService;
    private final InventoryRepository inventoryRepository;

    @Inject
    public InventoryShareService(
            InventoryShareRepository repository,
            FederationService federationService,
            InventoryRepository inventoryRepository) {
        this.repository = repository;
        this.federationService = federationService;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Resolves everything one station offers one partner into a policy that answers single items
     * without further queries.
     *
     * @param ownerStationId     the station whose gear it is
     * @param partnerStationUid  the station that would like to borrow it
     */
    public SharePolicy policyFor(int ownerStationId, UUID partnerStationUid) {
        var partner = findPartnership(ownerStationId, partnerStationUid);
        if (partner == null) return SharePolicy.closed();
        if (!federationService.hasCapability(partner, CapabilityType.INVENTORY_LEND, Direction.EXPORT)) {
            return SharePolicy.closed();
        }

        var targets = new HashMap<Integer, Set<Integer>>();
        for (var target : repository.findTargetsByStation(ownerStationId)) {
            targets.computeIfAbsent(target.shareId(), id -> new HashSet<>()).add(target.partnerId());
        }

        var byInventory = new HashMap<Integer, Boolean>();
        var byItem = new HashMap<Integer, Boolean>();
        for (var share : repository.findByStation(ownerStationId)) {
            boolean grants = share.shareGrant() == ShareGrant.GRANT && reaches(share, targets, partner.id());
            if (share.aboutItem()) byItem.put(share.itemId(), grants);
            else byInventory.put(share.inventoryId(), grants);
        }
        return new SharePolicy(true, Map.copyOf(byInventory), Map.copyOf(byItem));
    }

    private static boolean reaches(InventoryShare share, Map<Integer, Set<Integer>> targets, int partnerId) {
        if (share.shareScope() == ShareScope.ALL_PARTNERS) return true;
        return targets.getOrDefault(share.id(), Set.of()).contains(partnerId);
    }

    private FederationPartner findPartnership(int ownerStationId, UUID partnerStationUid) {
        return federationService.findPartners(ownerStationId).stream()
                .filter(p -> Objects.equals(p.partnerStationId(), partnerStationUid))
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    /** Every statement a station has made about what it offers. */
    public List<InventoryShare> findShares(int stationId) {
        return repository.findByStation(stationId);
    }

    /** The partners one share names, empty when it reaches everybody. */
    public List<Integer> findTargets(int shareId) {
        return repository.findTargets(shareId);
    }

    public Optional<InventoryShare> findForInventory(int stationId, int inventoryId) {
        return repository.findForInventory(stationId, inventoryId);
    }

    public Optional<InventoryShare> findForItem(int stationId, int itemId) {
        return repository.findForItem(stationId, itemId);
    }

    /**
     * Puts a whole inventory on offer, or takes it back out of one.
     *
     * @throws NotFoundResponse when the inventory is not this station's
     */
    public InventoryShare setInventoryShare(
            int stationId, int inventoryId, ShareScope scope, ShareGrant grant, List<Integer> partnerIds) {
        requireOwnInventory(stationId, inventoryId);
        var share = repository.upsertInventoryShare(stationId, inventoryId, scope, grant);
        repository.setTargets(share.id(), scope == ShareScope.SPECIFIC ? partnerIds : List.of());
        log.info("Station {} set lending share {} on inventory {}", stationId, grant, inventoryId);
        return share;
    }

    /**
     * Puts one item on offer, or takes it back out of whatever its inventory offers.
     *
     * @throws NotFoundResponse when the item is not in one of this station's inventories
     */
    public InventoryShare setItemShare(
            int stationId, int itemId, ShareScope scope, ShareGrant grant, List<Integer> partnerIds) {
        requireOwnItem(stationId, itemId);
        var share = repository.upsertItemShare(stationId, itemId, scope, grant);
        repository.setTargets(share.id(), scope == ShareScope.SPECIFIC ? partnerIds : List.of());
        log.info("Station {} set lending share {} on item {}", stationId, grant, itemId);
        return share;
    }

    /** Removes what was said about an inventory, which leaves it unshared unless a row is written again. */
    public boolean removeInventoryShare(int stationId, int inventoryId) {
        boolean removed = repository.deleteInventoryShare(stationId, inventoryId);
        if (removed) log.info("Station {} stopped offering inventory {}", stationId, inventoryId);
        return removed;
    }

    /** Removes what was said about an item, which puts it back under whatever its inventory says. */
    public boolean removeItemShare(int stationId, int itemId) {
        boolean removed = repository.deleteItemShare(stationId, itemId);
        if (removed) log.info("Station {} cleared the lending share on item {}", stationId, itemId);
        return removed;
    }

    private void requireOwnInventory(int stationId, int inventoryId) {
        boolean own = inventoryRepository.findByStation(stationId).stream().anyMatch(inv -> inv.id() == inventoryId);
        if (!own) throw new NotFoundResponse("This inventory does not belong to this station");
    }

    private void requireOwnItem(int stationId, int itemId) {
        var item = inventoryRepository.findItemById(itemId).orElseThrow(NotFoundResponse::new);
        requireOwnInventory(stationId, item.inventoryId());
    }

    /** The item ids of one inventory that are on offer to a partner. */
    public List<InventoryItem> filterShared(SharePolicy policy, List<InventoryItem> items) {
        return items.stream()
                .filter(item -> policy.allows(item.inventoryId(), item.id()))
                .toList();
    }
}
