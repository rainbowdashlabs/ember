/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Which chain a movement walks, and whose gear it is about.
 *
 * <p>Four values decide it: who owns the gear, which body that is when one on this instance does, who
 * the movement is with, and the chain those three resolve to. They are worked out in one place because
 * four callers need them: starting a movement, showing what a movement would walk before it is started,
 * writing down a piece that arrived, and moving an open movement onto a rewritten chain.
 */
@Singleton
public class MovementTargeting {
    private final InventoryRepository inventoryRepository;
    private final ClusterRepository clusterRepository;
    private final MovementFlowService flowService;

    @Inject
    public MovementTargeting(
            InventoryRepository inventoryRepository,
            ClusterRepository clusterRepository,
            MovementFlowService flowService) {
        this.inventoryRepository = inventoryRepository;
        this.clusterRepository = clusterRepository;
        this.flowService = flowService;
    }

    /**
     * What a movement with these ends would be about, and the chain it would walk.
     *
     * @param ownerKind      whose gear it is
     * @param ownerClusterId the owning body when one on this instance owns it, or {@code null}
     * @param party          the end that is not the owner: a member, or the station's store
     * @param flowId         the chain those three resolve to
     */
    public record Target(ItemOwner ownerKind, Integer ownerClusterId, MovementParty party, int flowId) {}

    /**
     * Works out the owner, the party and the chain for a movement that is about to be started, or for
     * one somebody is only being shown.
     *
     * <p>Both item ends stay separate, because which of them a caller knows follows from the purpose: a
     * return and an exchange name the piece that leaves, an issue names the piece that arrives, and a
     * request names neither.
     *
     * @param stationId      the station running it
     * @param purpose        what it is for
     * @param memberId       the member it concerns, or {@code null} for one about the store
     * @param outgoingItemId the piece leaving, or {@code null}
     * @param incomingItemId the piece arriving, where it is named up front
     * @param inventoryId    the inventory it is about, or {@code null}
     * @return the owner, the body, the party and the chain
     */
    public Target resolve(
            int stationId,
            MovementPurpose purpose,
            Integer memberId,
            Integer outgoingItemId,
            Integer incomingItemId,
            Integer inventoryId) {
        ItemOwner ownerKind = ownerOf(outgoingItemId, incomingItemId, inventoryId);
        Integer ownerClusterId = owningClusterOf(outgoingItemId != null ? outgoingItemId : incomingItemId, stationId);
        MovementParty party = memberId != null ? MovementParty.MEMBER : MovementParty.STORE;
        int flowId = flowService.resolveFlow(stationId, inventoryId, ownerKind, ownerClusterId, purpose, party);
        return new Target(ownerKind, ownerClusterId, party, flowId);
    }

    /**
     * What an open movement is about, read off the movement itself.
     *
     * @param movement the movement
     * @return the owner, the body, the party and the chain it belongs on
     */
    public Target of(ItemMovement movement) {
        return resolve(
                movement.stationId(),
                movement.purpose(),
                movement.memberId(),
                movement.outgoingItemId(),
                movement.incomingItemId(),
                movement.inventoryId());
    }

    /**
     * Who owns the piece a movement is about, which decides the chain it walks.
     *
     * <p>The piece itself is asked first, and either end will do: a return or an exchange names what is
     * leaving, while an issue or a request names only what is arriving. Asking the outgoing side alone
     * left every issue to the inventory instead, and a mixed inventory holds both kinds, so the
     * station's own gear went out along the chain written for the gear of the body above it.
     *
     * <p>Where neither end names a piece, the inventory answers as far as it can. A mixed one cannot, and
     * the body above the station is the better guess there: a movement with nothing on either side yet is
     * one asking for a piece the station does not hold.
     *
     * @param outgoingItemId the piece leaving, or {@code null}
     * @param incomingItemId the piece arriving, or {@code null}
     * @param inventoryId    the inventory it is about, or {@code null}
     * @return whose gear it is
     */
    public ItemOwner ownerOf(Integer outgoingItemId, Integer incomingItemId, Integer inventoryId) {
        ItemOwner named = ownerOfItem(outgoingItemId);
        if (named != null) return named;
        named = ownerOfItem(incomingItemId);
        if (named != null) return named;
        if (inventoryId == null) return ItemOwner.STATION;
        return inventoryRepository
                .findById(inventoryId)
                .map(inv -> inv.inventoryType() == InventoryType.INTERNAL ? ItemOwner.STATION : ItemOwner.CLUSTER)
                .orElse(ItemOwner.STATION);
    }

    /**
     * Who owns a replacement piece written down at the end of a movement.
     *
     * <p>The inventory answers first, because its type is the explicit statement about whose gear it
     * holds: internal gear is the station's and external gear is the body's above it, whatever the piece
     * that left had written on it. That matters exactly when the two disagree, which is the state an
     * inventory switched after its items were created used to leave behind: a replacement inheriting the
     * stale owner was refused by the very inventory it was coming home to. Only a mixed inventory has no
     * opinion, and there the piece that left answers, because a replacement belongs to whoever owned what
     * it replaces.
     *
     * @param movement the movement
     * @return the owner its replacement is recorded under
     */
    public ItemOwner ownerOfArrival(ItemMovement movement) {
        if (movement.inventoryId() != null) {
            var type = inventoryRepository
                    .findById(movement.inventoryId())
                    .map(Inventory::inventoryType)
                    .orElse(null);
            if (type == InventoryType.INTERNAL) return ItemOwner.STATION;
            if (type == InventoryType.EXTERNAL) return ItemOwner.CLUSTER;
        }
        return ownerOf(movement.outgoingItemId(), movement.incomingItemId(), movement.inventoryId());
    }

    /**
     * Which body owns the gear, when a body does.
     *
     * <p>Read off the item when there is one, because that is where ownership actually lives. An issue
     * that has not named its item yet falls back to the body the station answers to, which is the only
     * one whose gear could be arriving.
     *
     * @param itemId    the item, when the movement has one
     * @param stationId the station running the movement
     * @return the owning body, or {@code null} when no body owns it
     */
    public Integer owningClusterOf(Integer itemId, int stationId) {
        if (itemId != null) {
            Optional<InventoryItem> item = inventoryRepository.findItemById(itemId);
            if (item.isPresent()) return item.get().ownerClusterId();
        }
        return clusterRepository.findByStation(stationId).map(Cluster::id).orElse(null);
    }

    private ItemOwner ownerOfItem(Integer itemId) {
        if (itemId == null) return null;
        return inventoryRepository
                .findItemById(itemId)
                .map(InventoryItem::ownerKind)
                .orElse(null);
    }
}
