/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ProcurementCreated;
import dev.chojo.ember.event.events.ProcurementFulfilled;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.Procurement;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ProcurementRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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
     * Records something that has been ordered.
     *
     * @param memberId who it is for, or {@code null} for an order a cluster places for its own store
     */
    public Procurement create(int stationId, int inventoryId, Integer memberId, Integer sizeId, String notes) {
        inventoryService.requireHomogeneous(inventoryId, "ordering more");
        var procurement = procurementRepository.create(stationId, inventoryId, memberId, sizeId, notes);
        String inventoryName =
                inventoryRepository.findById(inventoryId).map(Inventory::name).orElse("?");
        // Nobody is told about an order that was for nobody
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

    public Optional<Procurement> findById(int id) {
        return procurementRepository.findById(id);
    }

    public List<Procurement> findByStation(int stationId) {
        return procurementRepository.findByStation(stationId);
    }

    public List<Procurement> findOpen(int stationId) {
        return procurementRepository.findOpen(stationId);
    }

    public boolean fulfill(int id) {
        var procurement = procurementRepository.findById(id);
        if (procurement.isEmpty()) {
            log.warn("Fulfill skipped: procurement {} not found", id);
            return false;
        }
        var proc = procurement.get();

        var inv = inventoryService.findById(proc.inventoryId());
        if (inv.isPresent()) {
            // What arrives belongs to whoever ordered it. At a cluster's own store that is the cluster,
            // and it rests there until the cluster sends it somewhere rather than landing on a person.
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
            String inventoryName = inventoryRepository
                    .findById(proc.inventoryId())
                    .map(Inventory::name)
                    .orElse("?");
            // Nobody is told about an order that was for nobody
            if (proc.memberId() != null) {
                eventBus.publish(
                        new ProcurementFulfilled(proc.stationId(), proc.memberId(), proc.inventoryId(), inventoryName));
            }
            log.info(
                    "Fulfilled procurement {} for member {} on inventory {} (station={})",
                    id,
                    proc.memberId(),
                    proc.inventoryId(),
                    proc.stationId());
            return true;
        }
        log.warn("Fulfill of procurement {} did not change any row", id);
        return false;
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
        } catch (BadRequestResponse noChain) {
            log.info(
                    "Procurement {} handed over directly: no chain serves an issue here ({})",
                    proc.id(),
                    noChain.getMessage());
            inventoryService.assignItem(item.id(), memberId, "");
        }
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
                .flatMap(member -> accountRepository.findById(member.accountId()))
                .map(account -> "%s %s"
                        .formatted(account.firstName(), account.lastName())
                        .trim())
                .orElse("");
    }

    public boolean delete(int id) {
        boolean deleted = procurementRepository.delete(id);
        if (deleted) log.info("Deleted procurement {}", id);
        else log.warn("Delete of procurement {} did not change any row", id);
        return deleted;
    }
}
