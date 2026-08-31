/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.inventory.entity.ArtStock;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.ItemNameCount;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the kinds of thing an inventory holds.
 *
 * <p>Everything here is scoped through the inventory alone, the way sizes and field definitions
 * are, because a kind belongs to exactly one inventory and an inventory to exactly one station.
 */
@Singleton
public class InventoryArtRepository {

    private static final String ART_COLUMNS = "id, inventory_id, name, note, position, merge_key";

    /**
     * Finds a kind by its identifier.
     *
     * @param id the identifier
     * @return the kind, or empty when there is none
     */
    public Optional<InventoryArt> findById(int id) {
        return SqlSupport.findById("inventory_art", ART_COLUMNS, id, InventoryArt.map());
    }

    /**
     * Every kind an inventory holds, in the order it shows them.
     *
     * @param inventoryId the inventory
     * @return its kinds, empty when it has none, which is the ordinary state
     */
    public List<InventoryArt> findByInventory(int inventoryId) {
        return query("""
                SELECT %s FROM inventory_art
                WHERE inventory_id = :inventory_id
                ORDER BY position, name;""", ART_COLUMNS)
                .single(call().bind("inventory_id", inventoryId))
                .map(InventoryArt.map())
                .all();
    }

    /**
     * The kinds of the same name anywhere on this instance, the current one among them.
     *
     * <p>This is the cross-station merge: two stations that write the same word mean the same kind,
     * compared after trimming and lowering, each keeping its own spelling on screen. The comparison
     * runs against the column the database maintains rather than a value written beside the name, so
     * renaming a kind cascades nowhere and the two spellings can never drift apart.
     *
     * @param mergeKey the key to match, as {@link InventoryArt#mergeKeyOf(String)} computes it
     * @return every kind sharing it
     */
    public List<InventoryArt> findByMergeKey(String mergeKey) {
        return query("""
                SELECT %s FROM inventory_art
                WHERE merge_key = :merge_key
                ORDER BY inventory_id, id;""", ART_COLUMNS)
                .single(call().bind("merge_key", mergeKey))
                .map(InventoryArt.map())
                .all();
    }

    /**
     * The kind of a given name inside one inventory, matched the way two stations match.
     *
     * @param inventoryId the inventory
     * @param name        the name as somebody typed it
     * @return the kind already there under that name, or empty
     */
    public Optional<InventoryArt> findByName(int inventoryId, String name) {
        return query("""
                SELECT %s FROM inventory_art
                WHERE inventory_id = :inventory_id AND merge_key = :merge_key;""", ART_COLUMNS)
                .single(call().bind("inventory_id", inventoryId).bind("merge_key", InventoryArt.mergeKeyOf(name)))
                .map(InventoryArt.map())
                .first();
    }

    /**
     * Writes down a new kind.
     *
     * @param inventoryId the inventory it belongs to
     * @param name        what the station calls it
     * @param note        a free note, may be empty
     * @param position    the sort position
     * @return the kind that was written
     */
    public InventoryArt create(int inventoryId, String name, String note, int position) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_art(inventory_id, name, note, position)
                VALUES(:inventory_id, :name, :note, :position)
                RETURNING %s;""",
                call().bind("inventory_id", inventoryId)
                        .bind("name", name)
                        .bind("note", note == null ? "" : note)
                        .bind("position", position),
                InventoryArt.map(),
                ART_COLUMNS);
    }

    /**
     * Renames a kind or moves it in the list. The merge key follows by itself.
     *
     * @param id       the kind
     * @param name     its new name
     * @param note     its new note
     * @param position its new sort position
     * @return {@code true} when a row changed
     */
    public boolean update(int id, String name, String note, int position) {
        return query("""
                UPDATE inventory_art
                SET name = :name, note = :note, position = :position
                WHERE id = :id;""")
                .single(call().bind("name", name)
                        .bind("note", note == null ? "" : note)
                        .bind("position", position)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Removes a kind.
     *
     * <p>The pieces stay and lose their kind, because the column is {@code ON DELETE SET NULL}. The
     * values they recorded for the kind's fields stay too, undescribed and unshown until a kind of
     * that name comes back, since throwing them away is the one irreversible option.
     *
     * @param id the kind
     * @return {@code true} when a row was removed
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("inventory_art", id);
    }

    /**
     * How many pieces an inventory holds of each kind, and how many of those are free.
     *
     * <p>Pieces with no kind get no row. A count over kinds leaves them out rather than inventing a
     * group for them, which is the whole reason nothing is seeded here.
     *
     * @param inventoryId the inventory
     * @return one row per kind that has at least one piece
     */
    public List<ArtStock> stockByInventory(int inventoryId) {
        return query("""
                SELECT a.id AS art_id,
                       a.name AS name,
                       count(*) AS pieces,
                       count(*) FILTER (WHERE %s AND ii.assigned_to IS NULL) AS free
                FROM inventory_art a
                JOIN inventory_item ii ON ii.art_id = a.id
                WHERE a.inventory_id = :inventory_id
                GROUP BY a.id, a.name, a.position
                ORDER BY a.position, a.name;""", ItemCustodySql.freeStock("ii"))
                .single(call().bind("inventory_id", inventoryId))
                .map(ArtStock.map())
                .all();
    }

    /**
     * How many pieces of one kind are free right now, wherever that kind is asked about.
     *
     * @param artId the kind
     * @return the number of pieces of it that are here and unassigned
     */
    public int freeOfArt(int artId) {
        return SqlSupport.count("""
                SELECT count(*) FROM inventory_item ii
                WHERE ii.art_id = :art_id AND %s AND ii.assigned_to IS NULL;""", call().bind("art_id", artId), ItemCustodySql.freeStock("ii"));
    }

    /**
     * The distinct names written on the pieces of an inventory, with a count each.
     *
     * <p>What the tidying screen reads. Eighteen rows say nothing; six names with a six, a five, a
     * four and three ones say where the typo is.
     *
     * @param inventoryId the inventory
     * @return one row per distinct name, commonest first
     */
    public List<ItemNameCount> nameCounts(int inventoryId) {
        return query("""
                SELECT name,
                       count(*) AS pieces,
                       count(*) FILTER (WHERE art_id IS NULL) AS unassigned
                FROM inventory_item
                WHERE inventory_id = :inventory_id
                GROUP BY name
                ORDER BY count(*) DESC, name;""")
                .single(call().bind("inventory_id", inventoryId))
                .map(ItemNameCount.map())
                .all();
    }

    /**
     * Puts a set of pieces under one kind and rewrites their names to it.
     *
     * <p>The rewrite is the part that makes tidying up worth doing. Setting the kind leaves the name
     * alone, and the name is what every list, both exports and the notification texts read, so
     * without it {@code Funkgerät organge} would go on reading {@code Funkgerät organge} under a
     * heading that says otherwise. It is a destructive edit, which is why it belongs to somebody
     * deciding it and never to a migration.
     *
     * @param artId   the kind they all become
     * @param itemIds the pieces
     * @param name    the name to write on them
     * @return how many pieces were changed
     */
    public int mergeIntoArt(int artId, Collection<Integer> itemIds, String name) {
        if (itemIds.isEmpty()) return 0;
        return query("""
                UPDATE inventory_item
                SET art_id = :art_id, name = :name
                WHERE id = ANY(:ids);""")
                .single(call().bind("art_id", artId)
                        .bind("name", name)
                        .bind("ids", List.copyOf(itemIds), PostgreSqlTypes.INTEGER))
                .update()
                .rows();
    }

    /**
     * Puts a set of pieces under one kind and leaves their names exactly as they are.
     *
     * <p>The other half of the tidying screen: {@code Pager 01} to {@code Pager 16} are sixteen
     * pieces of one kind and sixteen names worth keeping.
     *
     * @param artId   the kind, or {@code null} to take the kind away again
     * @param itemIds the pieces
     * @return how many pieces were changed
     */
    public int setArt(Integer artId, Collection<Integer> itemIds) {
        if (itemIds.isEmpty()) return 0;
        return query("""
                UPDATE inventory_item
                SET art_id = :art_id
                WHERE id = ANY(:ids);""")
                .single(call().bind("art_id", artId).bind("ids", List.copyOf(itemIds), PostgreSqlTypes.INTEGER))
                .update()
                .rows();
    }
}
