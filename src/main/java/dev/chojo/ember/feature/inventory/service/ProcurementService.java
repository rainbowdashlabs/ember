/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ProcurementCreated;
import dev.chojo.ember.event.events.ProcurementFulfilled;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.Procurement;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ProcurementRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Singleton
public class ProcurementService {
    private static final Logger log = LoggerFactory.getLogger(ProcurementService.class);
    private final ProcurementRepository procurementRepository;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final ClusterRepository clusterRepository;
    private final ItemCustodyService custodyService;
    private final ItemMovementService movementService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final DomainEventBus eventBus;

    @Inject
    public ProcurementService(
            ProcurementRepository procurementRepository,
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            ClusterRepository clusterRepository,
            ItemCustodyService custodyService,
            ItemMovementService movementService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            DomainEventBus eventBus) {
        this.movementService = movementService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.procurementRepository = procurementRepository;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.clusterRepository = clusterRepository;
        this.custodyService = custodyService;
        this.eventBus = eventBus;
    }

    /**
     * Records something that has been ordered. Nobody is notified about an order that is for nobody.
     *
     * @param memberId who it is for, or {@code null} for an order a cluster places for its own store
     */
    public Procurement create(
            int stationId,
            int inventoryId,
            @Nullable Integer memberId,
            @Nullable Integer sizeId,
            @Nullable String notes) {
        String inventoryName = inventoryRepository
                .findById(inventoryId)
                .filter(inventory -> inventory.stationId() == stationId)
                .map(Inventory::name)
                .orElseThrow(InventoryRefusal.INVENTORY_NOT_HERE_ON_PROCUREMENT::raise);
        inventoryService.requireHomogeneous(
                inventoryId,
                InventoryRefusal.INVENTORY_NOT_HERE_FOR_ORDER,
                InventoryRefusal.INVENTORY_ORDER_ON_A_COLLECTION);
        var procurement = procurementRepository.create(stationId, inventoryId, memberId, sizeId, notes);
        if (memberId != null) {
            eventBus.publish(new ProcurementCreated(stationId, memberId, inventoryId, inventoryName));
        }
        log.info(
                "Created procurement {} for member {} on inventory {} (sizeId={}, station={})",
                procurement.id(),
                memberId,
                inventoryId,
                sizeId,
                stationId);
        return procurement;
    }

    /**
     * Orders the replacement a swap is waiting for, when nothing fitting is free in storage.
     *
     * <p>The swap stays on the step that names its arriving piece, and the order is linked to it: delivering
     * the order names the new piece there, and the swap walks on as if the piece had been picked by hand.
     * The order is for the swap's member, in the size the swap asks for, with the swap's reason as its note.
     * A swap that keeps the size names no new one, and the order is then for the size it gives back.
     *
     * <p>Only a swap of the station's own gear qualifies. Where the owning cluster names the piece, the
     * cluster sends it, and there is nothing for the station to order.
     *
     * <p>The swap's row is held while the order is written, so a call-off or a second order arriving at
     * the same moment waits for this one and then finds it.
     *
     * @param stationId  the station asking
     * @param movementId the swap
     * @return the order, linked to the swap
     * @throws RefusalResponse when the movement is not such a swap, does not stand on that step, already
     *                         waits for an order, or a fitting piece is free in storage
     */
    public Procurement createForSwap(int stationId, int movementId) {
        return Transactions.call(() -> orderForSwap(stationId, movementId));
    }

    private Procurement orderForSwap(int stationId, int movementId) {
        ItemMovement swap = movementService
                .findByIdForUpdate(movementId)
                .filter(movement -> movement.stationId() == stationId)
                .orElseThrow(InventoryRefusal.MOVEMENT_NOT_A_SWAP_TO_ORDER_FOR::raise);
        Integer inventoryId = swap.inventoryId();
        if (swap.purpose() != MovementPurpose.EXCHANGE
                || inventoryId == null
                || movementService.ownerOf(swap) != ItemOwner.STATION) {
            throw InventoryRefusal.MOVEMENT_NOT_A_SWAP_TO_ORDER_FOR.raise();
        }
        if (pickingStepOf(swap).isEmpty()) throw InventoryRefusal.MOVEMENT_NOT_WAITING_FOR_ITS_REPLACEMENT.raise();
        if (procurementRepository.findOpenByMovement(movementId).isPresent()) {
            throw InventoryRefusal.MOVEMENT_ALREADY_WAITS_FOR_AN_ORDER.raise();
        }
        Integer wantedSize = swap.newSizeId() != null ? swap.newSizeId() : swap.oldSizeId();
        if (!inventoryRepository.findFreeStock(inventoryId, wantedSize).isEmpty()) {
            throw InventoryRefusal.MOVEMENT_REPLACEMENT_IN_STOCK.raise();
        }
        var order = procurementRepository.create(
                stationId, inventoryId, swap.memberId(), wantedSize, swap.reason(), movementId);
        Integer memberId = swap.memberId();
        if (memberId != null) {
            String inventoryName = inventoryRepository
                    .findById(inventoryId)
                    .map(Inventory::name)
                    .orElse("?");
            Transactions.afterCommit(
                    () -> eventBus.publish(new ProcurementCreated(stationId, memberId, inventoryId, inventoryName)));
        }
        log.info(
                "Created procurement {} for the replacement of swap {} (station={})",
                order.id(),
                movementId,
                stationId);
        return order;
    }

    /**
     * The step of a swap that names its arriving piece, while the swap stands on it and has none yet.
     *
     * @param swap the swap
     * @return that step, or empty when the swap is not waiting for its replacement
     */
    private Optional<MovementFlowStep> pickingStepOf(ItemMovement swap) {
        Integer standing = swap.currentStepId();
        if (swap.state() != MovementState.OPEN || swap.incomingItemId() != null || standing == null) {
            return Optional.empty();
        }
        return movementService.stepsOf(swap).stream()
                .filter(step -> step.id() == standing)
                .filter(ItemMovementService::namesIncomingItem)
                .findFirst();
    }

    /**
     * The swap an order is to bring the replacement for, while that swap still waits for it.
     *
     * @param order the order
     * @return the waiting swap, or empty for an order of its own or one whose swap has moved on
     */
    public Optional<ItemMovement> waitingSwapOf(Procurement order) {
        Integer movementId = order.movementId();
        if (movementId == null || order.fulfilledAt() != null) return Optional.empty();
        return movementService.findById(movementId).filter(swap -> pickingStepOf(swap)
                .isPresent());
    }

    public Optional<Procurement> findById(int id) {
        return procurementRepository.findById(id);
    }

    public List<Procurement> findByStation(int stationId) {
        return procurementRepository.findByStation(stationId);
    }

    public List<Procurement> findOpen(int stationId) {
        return procurementRepository.findOpen(stationId);
    }

    /**
     * Marks an order as arrived. What arrives belongs to whoever ordered it: at a cluster's own store that
     * is the cluster, and the piece rests there rather than landing on a person.
     *
     * <p>An order a swap is waiting for names the new piece as the swap's replacement instead, and the
     * swap walks on as if the piece had been picked by hand. The order is marked delivered first, so the
     * swap no longer counts as waiting for it, and both happen in one transaction.
     *
     * @param id    the order
     * @param actor who records the delivery, and so answers the swap's step
     * @return {@code true} when the order was marked delivered
     */
    public boolean fulfill(int id, ItemMovementService.Actor actor) {
        var procurement = procurementRepository.findById(id);
        if (procurement.isEmpty()) {
            log.warn("Fulfill skipped: procurement {} not found", id);
            return false;
        }
        var proc = procurement.get();
        if (proc.fulfilledAt() != null) {
            log.warn("Fulfill skipped: procurement {} was delivered already", id);
            return false;
        }
        var swap = waitingSwapOf(proc);
        if (swap.isPresent()) {
            return Transactions.call(() -> {
                var inventory = inventoryService.findById(proc.inventoryId()).orElseThrow();
                var item = inventoryService.createItem(proc.inventoryId(), "", inventory.name(), proc.sizeId(), null);
                fillSwap(proc, swap.get(), item.id(), actor);
                announceFulfilled(proc);
                return true;
            });
        }

        var inv = inventoryService.findById(proc.inventoryId());
        if (inv.isPresent()) {
            var owner = clusterRepository.findByHomeStation(proc.stationId());
            var item = owner.isPresent()
                    ? inventoryService.createItem(
                            proc.inventoryId(),
                            "",
                            inv.get().name(),
                            proc.sizeId(),
                            null,
                            ItemOwner.CLUSTER,
                            owner.get().id())
                    : inventoryService.createItem(
                            proc.inventoryId(), "", inv.get().name(), proc.sizeId(), null);
            if (proc.memberId() != null) {
                handOver(proc, item);
            } else {
                custodyService.returnToOwner(item.id());
            }
        }

        if (procurementRepository.fulfill(id)) {
            announceFulfilled(proc);
            return true;
        }
        log.warn("Fulfill of procurement {} did not change any row", id);
        return false;
    }

    /**
     * Marks an order delivered and names the piece as the replacement of the swap waiting for it.
     *
     * @param proc   the order
     * @param swap   the swap waiting for it
     * @param itemId the piece that answers it
     * @param actor  who answers the swap's step
     */
    private void fillSwap(Procurement proc, ItemMovement swap, int itemId, ItemMovementService.Actor actor) {
        if (!procurementRepository.fulfill(proc.id())) throw InventoryRefusal.PROCUREMENT_NOT_FULFILLED.raise();
        int step = pickingStepOf(swap).orElseThrow().id();
        movementService.acknowledge(swap.id(), step, actor, reasonFor(proc), itemId);
        log.info("Procurement {} named item {} as the replacement of swap {}", proc.id(), itemId, swap.id());
    }

    /** Tells the member an order for them arrived, once that is written, and records it. */
    private void announceFulfilled(Procurement proc) {
        String inventoryName = inventoryRepository
                .findById(proc.inventoryId())
                .map(Inventory::name)
                .orElse("?");
        Integer memberId = proc.memberId();
        if (memberId != null) {
            Transactions.afterCommit(() -> eventBus.publish(
                    new ProcurementFulfilled(proc.stationId(), memberId, proc.inventoryId(), inventoryName)));
        }
        log.info(
                "Fulfilled procurement {} for member {} on inventory {} (station={})",
                proc.id(),
                proc.memberId(),
                proc.inventoryId(),
                proc.stationId());
    }

    /**
     * Puts the piece that arrived on its way to whoever it was ordered for.
     *
     * <p>An order is a promise to a member, and what arrives against it is not in their hands yet: it
     * is on the shelf, spoken for, waiting for somebody to give it to them at the next duty. That is a
     * movement, and writing the piece straight onto the member instead recorded a hand-over that had
     * not happened.
     *
     * <p>A station that has no chain for handing its own gear out keeps the old behaviour rather than
     * losing the piece to a refusal: the record then says what it always said.
     */
    private void handOver(Procurement proc, InventoryItem item) {
        Integer memberId = proc.memberId();
        if (memberId == null) return;
        try {
            movementService.create(
                    proc.stationId(),
                    MovementPurpose.ISSUE,
                    memberId,
                    memberName(memberId),
                    null,
                    proc.inventoryId(),
                    null,
                    proc.sizeId(),
                    reasonFor(proc),
                    new ItemMovementService.Actor(memberId, true),
                    item.id());
        } catch (RefusalResponse noChain) {
            if (noChain.refusal() != InventoryRefusal.MOVEMENT_FLOW_NOT_BOUND
                    && noChain.refusal() != InventoryRefusal.MOVEMENT_FLOW_HAS_NO_STEPS) {
                throw noChain;
            }
            log.info(
                    "Procurement {} handed over directly: no chain serves an issue here ({})",
                    proc.id(),
                    noChain.getMessage());
            inventoryService.assignItem(item.id(), memberId, "");
        }
    }

    /**
     * The pieces already lying in the station's storage that would answer an open order: the same
     * inventory, the same size, free in storage and not promised to any open movement.
     *
     * <p>An order for nobody, or one already settled, has nobody to hand a piece to and gets nothing.
     *
     * @param procurement the order
     * @return the matching pieces, ordered by name
     */
    public List<InventoryItem> stockFor(Procurement procurement) {
        if (procurement.memberId() == null || procurement.fulfilledAt() != null) return List.of();
        return inventoryRepository.findFreeStock(procurement.inventoryId(), procurement.sizeId());
    }

    /**
     * Settles an open order with a piece the station already has rather than one that was bought.
     *
     * <p>The piece goes on the station's chain for handing gear out to the member the order was for,
     * and the order is marked fulfilled. No new piece is written down, since nothing arrived. An order a
     * swap is waiting for names the piece as the swap's replacement instead, and the swap is what hands
     * it out.
     *
     * @param id     the order
     * @param itemId the piece from storage
     * @param actor  who is starting the hand-out
     * @return the movement that hands the piece out
     */
    public ItemMovement issueFromStock(int id, int itemId, ItemMovementService.Actor actor) {
        var proc = procurementRepository
                .findById(id)
                .filter(found -> found.fulfilledAt() == null)
                .orElseThrow(InventoryRefusal.PROCUREMENT_NOT_OPEN_FOR_STOCK::raise);
        Integer memberId = proc.memberId();
        if (memberId == null) throw InventoryRefusal.PROCUREMENT_FOR_NOBODY_FROM_STOCK.raise();
        if (stockFor(proc).stream().noneMatch(item -> item.id() == itemId)) {
            throw InventoryRefusal.PROCUREMENT_PIECE_NOT_IN_STOCK.raise();
        }
        var swap = waitingSwapOf(proc);
        if (swap.isPresent()) {
            return Transactions.call(() -> {
                fillSwap(proc, swap.get(), itemId, actor);
                return movementService.findById(swap.get().id()).orElseThrow();
            });
        }
        var movement = movementService.create(
                proc.stationId(),
                MovementPurpose.ISSUE,
                memberId,
                memberName(memberId),
                null,
                proc.inventoryId(),
                null,
                proc.sizeId(),
                reasonFor(proc),
                actor,
                itemId);
        procurementRepository.fulfill(id);
        log.info(
                "Procurement {} settled from storage with item {} on movement {} (station={})",
                id,
                itemId,
                movement.id(),
                proc.stationId());
        return movement;
    }

    /** What the movement says it is for, which is the order it came out of. */
    private String reasonFor(Procurement proc) {
        String notes = proc.notes();
        return notes == null || notes.isBlank()
                ? "Aus einer Beschaffung"
                : "Aus einer Beschaffung: %s".formatted(notes);
    }

    private String memberName(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .flatMap(member -> Optional.ofNullable(member.accountId()).flatMap(accountRepository::findById))
                .map(account -> NameParts.of(account).called())
                .orElse("");
    }

    public boolean delete(int id) {
        boolean deleted = procurementRepository.delete(id);
        if (deleted) log.info("Deleted procurement {}", id);
        else log.warn("Delete of procurement {} did not change any row", id);
        return deleted;
    }
}
