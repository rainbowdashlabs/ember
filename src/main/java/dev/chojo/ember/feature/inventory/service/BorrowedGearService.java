/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Keeps the rows a station has for gear belonging to somebody else.
 *
 * <p>Before this, a borrowed radio had no row at the station that borrowed it: lending wrote on the
 * owner's row and set it to "with a partner", and that was all. The borrower's only view was a
 * lending request, which is a process rather than a thing, so the radio could not go in a container,
 * could not be handed to a member and could not be walked in a check, because there was nothing to
 * point at.
 *
 * <p>Three things are true of every row this service makes, and each of them is the answer to a
 * question that would otherwise be left open:
 *
 * <ul>
 *   <li><b>It is ordinary.</b> An item row like any other, in an inventory like any other, so
 *       everything the inventory already does works without being rebuilt.
 *   <li><b>It is a snapshot.</b> The name, the identifier and the fields are copied as they stood at
 *       handover and are never touched again. Pushing every edit across would be one federation
 *       message per change on a link that can be down, and its failure is the bad kind: two stations
 *       quietly showing different things with neither knowing which is current.
 *   <li><b>It ends with the loan.</b> On return the row goes away entirely, so a stale snapshot never
 *       outlives the loan it was taken for. What both stations keep is the loan.
 * </ul>
 */
@Singleton
public class BorrowedGearService {
    private static final Logger log = LoggerFactory.getLogger(BorrowedGearService.class);

    /**
     * What the shelf is called until the station calls it something else. It is one name rather than
     * one per partner, because the question it answers is "what have we got here that is not ours".
     */
    private static final String DEFAULT_SHELF_NAME = "Geliehene Ausrüstung";

    private final InventoryRepository inventoryRepository;

    @Inject
    public BorrowedGearService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * The station's one shelf for gear belonging to somebody else, made on the first handover.
     *
     * <p>One shelf, not one per partner. Split by partner, "what is here that is not ours" needs
     * several screens read together, and every one-off loan leaves an empty shell behind for good.
     * It holds a drawer of different things by construction, which is what keeps borrowed gear out of
     * requirements and procurements.
     *
     * @param stationId the borrowing station
     * @return the shelf, created if this is the first time anything has been borrowed
     */
    public Inventory shelfAt(int stationId) {
        return inventoryRepository.findBorrowedInventory(stationId).orElseGet(() -> {
            Inventory created =
                    inventoryRepository.create(stationId, DEFAULT_SHELF_NAME, InventoryType.MIXED, false, false, true);
            log.info("Created the borrowed shelf {} for station {}", created.id(), stationId);
            return created;
        });
    }

    /**
     * Writes down a partner's piece at the station that has just taken it.
     *
     * <p>Nothing is asked of the owner's row here beyond reading it. The copy carries the name, the
     * identifier and the fields as they stood, and the owner stays free to correct any of them
     * afterwards without the borrower's screen changing under them.
     *
     * @param source             the owner's row, read once
     * @param owningStationId    the partner that owns it
     * @param borrowingStationId the station taking it
     * @param loanRequestItemId  the line of the lending request it came in on
     * @return the row written at the borrower, or empty when one already exists for that line
     */
    public Optional<InventoryItem> handOver(
            InventoryItem source, int owningStationId, int borrowingStationId, int loanRequestItemId) {
        if (!inventoryRepository.findBorrowedByLoanItem(loanRequestItemId).isEmpty()) {
            log.info("Handover of loan line {} already has a row at station {}", loanRequestItemId, borrowingStationId);
            return Optional.empty();
        }
        Inventory shelf = shelfAt(borrowingStationId);
        InventoryItem copy = inventoryRepository.createBorrowedItem(
                shelf.id(), source.internalId(), source.name(), source.metadata(), owningStationId, loanRequestItemId);
        log.info(
                "Item {} of station {} was written down as {} at borrowing station {} on loan line {}",
                source.id(),
                owningStationId,
                copy.id(),
                borrowingStationId,
                loanRequestItemId);
        return Optional.of(copy);
    }

    /**
     * Takes the borrowed rows of one loan line away again, which is what a return means at the
     * borrower.
     *
     * <p>The row goes rather than being marked returned. It was never the borrower's record of a
     * thing, only of where a thing was this fortnight, and what is worth keeping at both ends is the
     * loan itself, which stays.
     *
     * @param loanRequestItemId the line of the lending request going home
     * @return how many rows went
     */
    public int handBack(int loanRequestItemId) {
        List<InventoryItem> rows = inventoryRepository.findBorrowedByLoanItem(loanRequestItemId);
        for (InventoryItem row : rows) {
            inventoryRepository.deleteItem(row.id());
            log.info("Borrowed row {} went home with loan line {}", row.id(), loanRequestItemId);
        }
        return rows.size();
    }

    /**
     * Everything a station is holding that is not its own, named with the partner it belongs to and
     * the day it goes back.
     *
     * @param stationId the borrowing station
     * @return one entry per borrowed piece, by partner and then by name
     */
    public List<InventoryRepository.BorrowedItem> borrowedAt(int stationId) {
        return inventoryRepository.findBorrowedItems(stationId);
    }
}
