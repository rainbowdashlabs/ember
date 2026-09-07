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
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import io.javalin.http.BadRequestResponse;
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
    private final InventoryArtRepository artRepository;

    @Inject
    public InventoryShareService(
            InventoryShareRepository repository,
            FederationService federationService,
            InventoryRepository inventoryRepository,
            InventoryArtRepository artRepository) {
        this.repository = repository;
        this.federationService = federationService;
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
    }

    /**
     * Resolves everything one station offers one partner into a policy that answers single items
     * without further queries.
     *
     * @param ownerStationId     the station whose gear it is
     * @param partnerStationUid  the station that would like to borrow it
     */
    public SharePolicy policyFor(int ownerStationId, UUID partnerStationUid) {
        return policyFrom(ownerStationId, findPartnership(ownerStationId, partnerStationUid));
    }

    /**
     * The same policy for a partnership already in hand, which is what a request arriving over
     * federation carries instead of the asking station's uid.
     *
     * @param ownerStationId the station whose gear it is
     * @param partnerId      the partnership the request arrived on
     */
    public SharePolicy policyForPartnership(int ownerStationId, int partnerId) {
        var partner = federationService.findPartners(ownerStationId).stream()
                .filter(p -> p.id() == partnerId)
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .findFirst()
                .orElse(null);
        return policyFrom(ownerStationId, partner);
    }

    private SharePolicy policyFrom(int ownerStationId, FederationPartner partner) {
        if (partner == null) return SharePolicy.closed();
        if (!federationService.hasCapability(partner, CapabilityType.INVENTORY_LEND, Direction.EXPORT)) {
            return SharePolicy.closed();
        }

        var targets = new HashMap<Integer, Set<Integer>>();
        for (var target : repository.findTargetsByStation(ownerStationId)) {
            targets.computeIfAbsent(target.shareId(), id -> new HashSet<>()).add(target.partnerId());
        }

        var byInventory = new HashMap<Integer, Boolean>();
        var byArt = new HashMap<Integer, Boolean>();
        var byItem = new HashMap<Integer, Boolean>();
        for (var share : repository.findByStation(ownerStationId)) {
            boolean grants = share.shareGrant() == ShareGrant.GRANT && reaches(share, targets, partner.id());
            switch (share.level()) {
                case ITEM -> byItem.put(share.itemId(), grants);
                case ART -> byArt.put(share.artId(), grants);
                case INVENTORY -> byInventory.put(share.inventoryId(), grants);
            }
        }
        return new SharePolicy(true, Map.copyOf(byInventory), Map.copyOf(byArt), Map.copyOf(byItem));
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

    public Optional<InventoryShare> findForArt(int stationId, int artId) {
        return repository.findForArt(stationId, artId);
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
     * Puts one kind of thing on offer, or takes it back out of whatever its inventory offers. It is
     * the level the drawer of odds and ends is really described at: the good radios go, the cheap
     * ones stay, and neither choice has to be made again when a piece is added.
     *
     * @throws NotFoundResponse when the kind is not in one of this station's inventories
     */
    public InventoryShare setArtShare(
            int stationId, int artId, ShareScope scope, ShareGrant grant, List<Integer> partnerIds) {
        requireOwnArt(stationId, artId);
        var share = repository.upsertArtShare(stationId, artId, scope, grant);
        repository.setTargets(share.id(), scope == ShareScope.SPECIFIC ? partnerIds : List.of());
        log.info("Station {} set lending share {} on kind {}", stationId, grant, artId);
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

    /** Removes what was said about a kind, which puts it back under whatever its inventory says. */
    public boolean removeArtShare(int stationId, int artId) {
        boolean removed = repository.deleteArtShare(stationId, artId);
        if (removed) log.info("Station {} cleared the lending share on kind {}", stationId, artId);
        return removed;
    }

    /** Removes what was said about an item, which puts it back under whatever its kind says. */
    public boolean removeItemShare(int stationId, int itemId) {
        boolean removed = repository.deleteItemShare(stationId, itemId);
        if (removed) log.info("Station {} cleared the lending share on item {}", stationId, itemId);
        return removed;
    }

    /**
     * The inventory an offer may be written on, which is one this station owns and may lend out of.
     *
     * <p>An external inventory holds nothing but gear of the body above the station, and a station
     * lends only what is its own, so an offer written there could never be filled. A mixed one is a
     * different case and stays open: the station's own pieces stand in it beside the body's, and the
     * pieces that are not the station's are dropped where the offer is read rather than here.
     */
    private Inventory requireOwnInventory(int stationId, int inventoryId) {
        Inventory inventory = inventoryRepository.findByStation(stationId).stream()
                .filter(inv -> inv.id() == inventoryId)
                .findFirst()
                .orElseThrow(() -> new NotFoundResponse("This inventory does not belong to this station"));
        if (inventory.inventoryType() == InventoryType.EXTERNAL) {
            throw new BadRequestResponse(
                    "This inventory holds gear of the body above the station, which the station cannot lend out");
        }
        return inventory;
    }

    private void requireOwnArt(int stationId, int artId) {
        var art = artRepository.findById(artId).orElseThrow(NotFoundResponse::new);
        requireOwnInventory(stationId, art.inventoryId());
    }

    private void requireOwnItem(int stationId, int itemId) {
        var item = inventoryRepository.findItemById(itemId).orElseThrow(NotFoundResponse::new);
        requireOwnInventory(stationId, item.inventoryId());
    }

    /** The pieces of one inventory that are on offer to a partner. */
    public List<InventoryItem> filterShared(SharePolicy policy, List<InventoryItem> items) {
        return items.stream()
                .filter(item -> policy.allows(item.inventoryId(), item.artId(), item.id()))
                .toList();
    }
}
