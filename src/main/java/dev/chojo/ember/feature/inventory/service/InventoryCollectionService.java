/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.CollectionLine;
import dev.chojo.ember.feature.inventory.entity.InventoryCollection;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.inventory.entity.ResolvedCollection;
import dev.chojo.ember.feature.inventory.entity.ResolvedCollectionLine;
import dev.chojo.ember.feature.inventory.repository.InventoryCollectionRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Collections: the named sets a station keeps so that what belongs together stays together.
 *
 * <p>A collection carries no promise. It is a template, its lines are copied wherever they are used,
 * and nothing here reserves anything. What it can do is say what it would find, which is
 * {@link #resolve(int, int, LocalDate, LocalDate)}.
 *
 * <p>Everything a collection points at has to belong to the same station, checked here rather than at
 * the route, because a line naming another station's gear would resolve to nothing for ever and say
 * nothing about why.
 */
@Singleton
public class InventoryCollectionService {

    private final InventoryCollectionRepository collectionRepository;
    private final LineTargetService lineTargets;

    @Inject
    public InventoryCollectionService(
            InventoryCollectionRepository collectionRepository, LineTargetService lineTargets) {
        this.collectionRepository = collectionRepository;
        this.lineTargets = lineTargets;
    }

    /**
     * Finds a collection by its ID.
     *
     * @param id the collection ID
     * @return the collection, or empty if not found
     */
    public Optional<InventoryCollection> findById(int id) {
        return collectionRepository.findById(id);
    }

    /**
     * Every collection of a station, with the number of lines each carries.
     *
     * @param stationId the station ID
     * @return the collections
     */
    public List<InventoryCollectionRepository.CollectionSummary> findByStation(int stationId) {
        return collectionRepository.findSummariesByStation(stationId);
    }

    /**
     * The lines of a collection, in their own order.
     *
     * @param collectionId the collection ID
     * @return the lines
     */
    public List<CollectionLine> findLines(int collectionId) {
        return collectionRepository.findLines(collectionId);
    }

    /**
     * Creates a collection.
     *
     * @param stationId the station it belongs to
     * @param name      what the station calls it
     * @param note      free text about its purpose
     * @param createdBy the member creating it, or {@code null}
     * @return the created collection
     * @throws IllegalArgumentException if the name is blank
     */
    public InventoryCollection create(int stationId, String name, String note, Integer createdBy) {
        String trimmed = requireName(name);
        return collectionRepository.create(stationId, trimmed, note == null ? "" : note.trim(), createdBy);
    }

    /**
     * Renames a collection and rewrites its note.
     *
     * @param id   the collection ID
     * @param name the new name
     * @param note the new note
     * @return {@code true} if a row changed
     * @throws IllegalArgumentException if the name is blank
     */
    public boolean update(int id, String name, String note) {
        String trimmed = requireName(name);
        return collectionRepository.update(id, trimmed, note == null ? "" : note.trim());
    }

    /**
     * Deletes a collection and its lines.
     *
     * @param id        the collection ID
     * @param stationId the station it must belong to
     * @return {@code true} if a row went
     */
    public boolean delete(int id, int stationId) {
        return collectionRepository.delete(id, stationId);
    }

    /**
     * Appends a line naming one piece.
     *
     * <p>A named piece is one piece, so this line never carries a count.
     *
     * @param collectionId the collection to append to
     * @param stationId    the station the collection belongs to
     * @param itemId       the piece to name
     * @return the created line
     * @throws IllegalArgumentException if the piece belongs to another station or is already named here
     */
    public CollectionLine addItemLine(int collectionId, int stationId, int itemId) {
        lineTargets.requireOwnedBy(
                LineTarget.item(itemId), stationId, "A collection can only name gear of its own station");
        if (collectionRepository.findLines(collectionId).stream()
                .anyMatch(line -> Integer.valueOf(itemId).equals(line.itemId()))) {
            throw new IllegalArgumentException("The collection already names this item");
        }
        return collectionRepository.addLine(collectionId, itemId, null, null, 1);
    }

    /**
     * Appends a line asking for a count of one kind of thing.
     *
     * <p>This is the line the whole idea turns on: four blue radios rather than four of whatever the
     * radio drawer happens to hold, which in a drawer that also holds a charging station and a cable
     * are two different requests.
     *
     * @param collectionId the collection to append to
     * @param stationId    the station the collection belongs to
     * @param artId        the kind to ask for
     * @param quantity     how many pieces to ask for
     * @return the created line
     * @throws IllegalArgumentException if the kind belongs to another station or the count is below one
     */
    public CollectionLine addArtLine(int collectionId, int stationId, int artId, int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("A line asks for at least one piece");
        lineTargets.requireOwnedBy(
                LineTarget.art(artId), stationId, "A collection can only ask for its own station's kinds");
        return collectionRepository.addLine(collectionId, null, artId, null, quantity);
    }

    /**
     * Appends a line asking for a count out of a whole inventory.
     *
     * <p>For the inventories that hold one thing in many copies, which carry no kinds at all.
     *
     * @param collectionId the collection to append to
     * @param stationId    the station the collection belongs to
     * @param inventoryId  the inventory to draw from
     * @param quantity     how many pieces to ask for
     * @return the created line
     * @throws IllegalArgumentException if the inventory belongs to another station or the count is below one
     */
    public CollectionLine addInventoryLine(int collectionId, int stationId, int inventoryId, int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("A line asks for at least one piece");
        lineTargets.requireOwnedBy(
                LineTarget.inventory(inventoryId),
                stationId,
                "A collection can only draw from its own station's inventories");
        return collectionRepository.addLine(collectionId, null, null, inventoryId, quantity);
    }

    /**
     * Changes how many pieces a counted line asks for.
     *
     * @param lineId   the line ID
     * @param quantity the new count
     * @return {@code true} if a row changed
     * @throws IllegalArgumentException if the count is below one or the line names a single piece
     */
    public boolean updateLineQuantity(int lineId, int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("A line asks for at least one piece");
        CollectionLine line = collectionRepository
                .findLine(lineId)
                .orElseThrow(() -> new IllegalArgumentException("The line does not exist"));
        if (line.namesItem()) {
            throw new IllegalArgumentException("A line naming one piece always asks for that one piece");
        }
        return collectionRepository.updateLineQuantity(lineId, quantity);
    }

    /**
     * Rewrites the order of a collection's lines.
     *
     * @param collectionId the collection whose lines are being ordered
     * @param orderedIds   the line IDs in their new order
     */
    public void reorderLines(int collectionId, List<Integer> orderedIds) {
        collectionRepository.reorderLines(collectionId, orderedIds);
    }

    /**
     * Deletes a line.
     *
     * @param lineId the line ID
     * @return {@code true} if a row went
     */
    public boolean deleteLine(int lineId) {
        return collectionRepository.deleteLine(lineId);
    }

    /**
     * Reads a collection against what the station can put its hands on.
     *
     * @param collectionId the collection to read
     * @param stationId    the station doing the reading
     * @param dateFrom     the first day of the window, or {@code null} to read undated
     * @param dateTo       the last day of the window, or {@code null} to read undated
     * @return the collection with one answer per line
     * @throws IllegalArgumentException if the collection does not exist or the window ends before it starts
     */
    public ResolvedCollection resolve(int collectionId, int stationId, LocalDate dateFrom, LocalDate dateTo) {
        InventoryCollection collection = collectionRepository
                .findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("The collection does not exist"));
        LocalDate from = dateFrom;
        LocalDate to = dateTo == null ? dateFrom : dateTo;
        if (from == null) to = null;
        if (from != null && to.isBefore(from)) {
            throw new IllegalArgumentException("The window ends before it starts");
        }
        List<ResolvedCollectionLine> lines = collectionRepository.resolve(collectionId, stationId, from, to);
        return new ResolvedCollection(collection, from, to, lines);
    }

    /**
     * The collections that stand to lose a line when a piece goes.
     *
     * @param itemId the piece about to go
     * @return the collections holding it
     */
    public List<InventoryCollection> collectionsHoldingItem(int itemId) {
        return collectionRepository.findCollectionsHoldingItem(itemId);
    }

    /**
     * The collections that stand to lose a line when an inventory goes.
     *
     * @param inventoryId the inventory about to go
     * @return the collections affected
     */
    public List<InventoryCollection> collectionsTouchingInventory(int inventoryId) {
        return collectionRepository.findCollectionsTouchingInventory(inventoryId);
    }

    /**
     * The collections that stand to lose a line when a kind of thing goes.
     *
     * @param artId the kind about to go
     * @return the collections asking for it
     */
    public List<InventoryCollection> collectionsAskingForArt(int artId) {
        return collectionRepository.findCollectionsAskingForArt(artId);
    }

    private String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("A collection needs a name");
        return trimmed;
    }
}
