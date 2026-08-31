/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.inventory.entity.CollectionLine;
import dev.chojo.ember.feature.inventory.entity.InventoryCollection;
import dev.chojo.ember.feature.inventory.entity.ResolvedCollectionLine;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Collections and their lines, and the one query that reads a collection against the stock.
 */
@Singleton
public class InventoryCollectionRepository {

    private static final String COLLECTION_COLUMNS = "id, station_id, name, note, created_by, created_at";
    private static final String LINE_COLUMNS =
            "id, collection_id, item_id, art_id, inventory_id, quantity, position";

    /**
     * Finds a collection by its ID.
     *
     * @param id the collection ID
     * @return the collection, or empty if not found
     */
    public Optional<InventoryCollection> findById(int id) {
        return SqlSupport.findById("inventory_collection", COLLECTION_COLUMNS, id, InventoryCollection.map());
    }

    /**
     * Every collection of a station with the number of lines it carries, for the list screen.
     *
     * @param stationId the station ID
     * @return the collections, ordered by name
     */
    public List<CollectionSummary> findSummariesByStation(int stationId) {
        return query("""
                SELECT %s, count(l.id) AS line_count
                FROM inventory_collection c
                LEFT JOIN inventory_collection_line l ON l.collection_id = c.id
                WHERE c.station_id = :station_id
                GROUP BY c.id
                ORDER BY lower(c.name), c.id;""", SqlSupport.alias("c", COLLECTION_COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(row -> new CollectionSummary(InventoryCollection.map().map(row), row.getInt("line_count")))
                .all();
    }

    /**
     * A collection as a list screen reads it.
     *
     * @param collection the collection itself
     * @param lineCount  how many lines it carries
     */
    public record CollectionSummary(InventoryCollection collection, int lineCount) {}

    /**
     * Creates a collection.
     *
     * @param stationId the station it belongs to
     * @param name      what the station calls it
     * @param note      free text about its purpose
     * @param createdBy the member creating it, or {@code null}
     * @return the created collection
     */
    public InventoryCollection create(int stationId, String name, String note, Integer createdBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_collection(station_id, name, note, created_by)
                VALUES (:station_id, :name, :note, :created_by)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("note", note)
                        .bind("created_by", createdBy),
                InventoryCollection.map(),
                COLLECTION_COLUMNS);
    }

    /**
     * Renames a collection and rewrites its note.
     *
     * @param id   the collection ID
     * @param name the new name
     * @param note the new note
     * @return {@code true} if a row changed
     */
    public boolean update(int id, String name, String note) {
        return query("UPDATE inventory_collection SET name = :name, note = :note WHERE id = :id;")
                .single(call().bind("id", id).bind("name", name).bind("note", note))
                .update()
                .changed();
    }

    /**
     * Deletes a collection and, with it, its lines.
     *
     * @param id        the collection ID
     * @param stationId the station it must belong to
     * @return {@code true} if a row went
     */
    public boolean delete(int id, int stationId) {
        return SqlSupport.deleteByIdInStation("inventory_collection", id, stationId);
    }

    /**
     * The lines of a collection in their own order.
     *
     * @param collectionId the collection ID
     * @return the lines
     */
    public List<CollectionLine> findLines(int collectionId) {
        return query("""
                SELECT %s FROM inventory_collection_line
                WHERE collection_id = :collection_id
                ORDER BY position, id;""", LINE_COLUMNS)
                .single(call().bind("collection_id", collectionId))
                .map(CollectionLine.map())
                .all();
    }

    /**
     * Finds a line by its ID.
     *
     * @param id the line ID
     * @return the line, or empty if not found
     */
    public Optional<CollectionLine> findLine(int id) {
        return SqlSupport.findById("inventory_collection_line", LINE_COLUMNS, id, CollectionLine.map());
    }

    /**
     * Appends a line to a collection, behind whatever is already there.
     *
     * @param collectionId the collection ID
     * @param itemId       the named piece, or {@code null}
     * @param artId        the kind of thing counted, or {@code null}
     * @param inventoryId  the inventory drawn from, or {@code null}
     * @param quantity     how many pieces the line asks for
     * @return the created line
     */
    public CollectionLine addLine(
            int collectionId, Integer itemId, Integer artId, Integer inventoryId, int quantity) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_collection_line(collection_id, item_id, art_id, inventory_id, quantity, position)
                VALUES (:collection_id, :item_id, :art_id, :inventory_id, :quantity,
                        (SELECT coalesce(max(position) + 1, 0) FROM inventory_collection_line
                          WHERE collection_id = :collection_id))
                RETURNING %s;""",
                call().bind("collection_id", collectionId)
                        .bind("item_id", itemId)
                        .bind("art_id", artId)
                        .bind("inventory_id", inventoryId)
                        .bind("quantity", quantity),
                CollectionLine.map(),
                LINE_COLUMNS);
    }

    /**
     * Changes how many pieces a counted line asks for.
     *
     * @param id       the line ID
     * @param quantity the new count
     * @return {@code true} if a row changed
     */
    public boolean updateLineQuantity(int id, int quantity) {
        return query("UPDATE inventory_collection_line SET quantity = :quantity WHERE id = :id;")
                .single(call().bind("id", id).bind("quantity", quantity))
                .update()
                .changed();
    }

    /**
     * Rewrites the order of a collection's lines.
     *
     * @param collectionId the collection whose lines are being ordered
     * @param orderedIds   the line IDs in their new order
     */
    public void reorderLines(int collectionId, List<Integer> orderedIds) {
        SqlSupport.reorder("inventory_collection_line", "position", "collection_id", collectionId, orderedIds);
    }

    /**
     * Deletes a line.
     *
     * @param id the line ID
     * @return {@code true} if a row went
     */
    public boolean deleteLine(int id) {
        return SqlSupport.deleteById("inventory_collection_line", id);
    }

    /**
     * Reads a collection against what its station can put its hands on, over a window.
     *
     * <p>The predicate is {@link ItemCustodySql#atHand(String, String)} rather than free stock: a kit
     * that reports itself unfillable because the station's own radios are in the station's own hands
     * would be useless. The window subtracts what is already promised to an approved or running
     * lending request that overlaps it; passing no window subtracts nothing, which is the honest
     * answer to "what is here right now".
     *
     * @param collectionId the collection to read
     * @param stationId    the station doing the reading
     * @param dateFrom     the first day of the window, or {@code null} to read undated
     * @param dateTo       the last day of the window, or {@code null} to read undated
     * @return one answer per line, in the collection's order
     */
    public List<ResolvedCollectionLine> resolve(int collectionId, int stationId, LocalDate dateFrom, LocalDate dateTo) {
        return query("""
                SELECT l.id            AS line_id,
                       l.item_id,
                       l.art_id,
                       l.inventory_id,
                       l.quantity,
                       coalesce(it.name, art.name, inv.name, '') AS label,
                       found.available,
                       found.cluster_owned
                FROM inventory_collection_line l
                LEFT JOIN inventory_item it ON it.id = l.item_id
                LEFT JOIN inventory_art art ON art.id = l.art_id
                LEFT JOIN inventory inv ON inv.id = l.inventory_id
                LEFT JOIN LATERAL (
                    SELECT count(*)                                          AS available,
                           count(*) FILTER (WHERE ci.owner_kind <> 'STATION') AS cluster_owned
                    FROM inventory_item ci
                    %1$s
                    WHERE (ci.id = l.item_id
                           OR ci.art_id = l.art_id
                           OR ci.inventory_id = l.inventory_id)
                      AND %2$s
                      AND NOT EXISTS (
                          SELECT 1 FROM federation_lending_request_item li
                          JOIN federation_lending_request lr ON lr.id = li.request_id
                          WHERE li.assigned_item_id = ci.id
                            AND lr.status IN ('APPROVED', 'LENT')
                            AND :date_from::DATE IS NOT NULL
                            AND lr.requested_date_from <= :date_to::DATE
                            AND (lr.requested_date_to IS NULL OR lr.requested_date_to >= :date_from::DATE)
                      )
                ) found ON TRUE
                WHERE l.collection_id = :collection_id
                ORDER BY l.position, l.id;""", ItemCustodySql.joinInventory("ci", "cinv"), ItemCustodySql.atHand("ci", "cinv"))
                .single(call().bind("collection_id", collectionId)
                        .bind(ItemCustodySql.STATION_BIND, stationId)
                        .bind("date_from", dateFrom)
                        .bind("date_to", dateTo))
                .map(row -> new ResolvedCollectionLine(
                        row.getInt("line_id"),
                        row.getObject("item_id", Integer.class),
                        row.getObject("art_id", Integer.class),
                        row.getObject("inventory_id", Integer.class),
                        row.getString("label"),
                        row.getInt("quantity"),
                        row.getInt("available"),
                        row.getInt("cluster_owned")))
                .all();
    }

    /**
     * The collections that stand to lose a line when a piece goes.
     *
     * <p>Read before the deletion, so the dialog can name them. The line itself cascades, which is
     * what makes this the only warning there is.
     *
     * @param itemId the piece about to go
     * @return the collections holding it, ordered by name
     */
    public List<InventoryCollection> findCollectionsHoldingItem(int itemId) {
        return query("""
                SELECT %s FROM inventory_collection c
                WHERE EXISTS (SELECT 1 FROM inventory_collection_line l
                               WHERE l.collection_id = c.id AND l.item_id = :item_id)
                ORDER BY lower(c.name), c.id;""", SqlSupport.alias("c", COLLECTION_COLUMNS))
                .single(call().bind("item_id", itemId))
                .map(InventoryCollection.map())
                .all();
    }

    /**
     * The collections that stand to lose a line when a kind of thing goes.
     *
     * <p>Removing a kind leaves its pieces alone, which is what makes this warning worth showing: the
     * radios are all still there, and the line that asked for four blue ones is not.
     *
     * @param artId the kind about to go
     * @return the collections asking for it, ordered by name
     */
    public List<InventoryCollection> findCollectionsAskingForArt(int artId) {
        return query("""
                SELECT %s FROM inventory_collection c
                WHERE EXISTS (SELECT 1 FROM inventory_collection_line l
                               WHERE l.collection_id = c.id AND l.art_id = :art_id)
                ORDER BY lower(c.name), c.id;""", SqlSupport.alias("c", COLLECTION_COLUMNS))
                .single(call().bind("art_id", artId))
                .map(InventoryCollection.map())
                .all();
    }

    /**
     * The collections that stand to lose a line when an inventory goes.
     *
     * <p>Three ways to lose one, because deleting an inventory takes both its pieces and its kinds
     * with it: a counted line drawing from the inventory, a line asking for one of its kinds, and a
     * named line pointing at a piece filed in it.
     *
     * @param inventoryId the inventory about to go
     * @return the collections affected, ordered by name
     */
    public List<InventoryCollection> findCollectionsTouchingInventory(int inventoryId) {
        return query("""
                SELECT %s FROM inventory_collection c
                WHERE EXISTS (SELECT 1 FROM inventory_collection_line l
                               LEFT JOIN inventory_item it ON it.id = l.item_id
                               LEFT JOIN inventory_art art ON art.id = l.art_id
                               WHERE l.collection_id = c.id
                                 AND (l.inventory_id = :inventory_id
                                      OR it.inventory_id = :inventory_id
                                      OR art.inventory_id = :inventory_id))
                ORDER BY lower(c.name), c.id;""", SqlSupport.alias("c", COLLECTION_COLUMNS))
                .single(call().bind("inventory_id", inventoryId))
                .map(InventoryCollection.map())
                .all();
    }
}
