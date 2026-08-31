/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.inventory.entity.InventoryTag;
import dev.chojo.ember.feature.inventory.entity.TaggedItemSummary;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The tags a station puts on its items, and which item carries which.
 *
 * <p>Every query that treats two stations' tags as one compares {@code canonical_name}, the trimmed
 * lowercase form the database maintains. Nothing here builds that form in SQL by hand, so a query
 * cannot quietly return too few rows by forgetting it.
 */
@Singleton
public class InventoryTagRepository {
    private static final String INVENTORY_TAG_COLUMNS = "id, station_id, name, canonical_name, color, position";

    private static final String TAGGED_ITEM_SELECT = """
            SELECT i.id                                                    AS item_id,
                   i.internal_id                                           AS internal_id,
                   i.name                                                  AS item_name,
                   inv.id                                                  AS inventory_id,
                   inv.name                                                AS inventory_name,
                   i.art_id                                                AS art_id,
                   s.uid                                                   AS station_uid,
                   s.name                                                  AS station_name,
                   t.name                                                  AS tag_name,
                   (i.assigned_to IS NULL AND i.lost_at IS NULL)           AS available
            FROM inventory_item i
                     JOIN inventory inv ON inv.id = i.inventory_id
                     JOIN station s ON s.id = inv.station_id
                     JOIN inventory_item_tag it ON it.item_id = i.id
                     JOIN inventory_tag t ON t.id = it.tag_id AND t.station_id = inv.station_id
            """;

    /**
     * Creates a tag, placing it after the station's existing ones.
     *
     * @param stationId the station
     * @param name      the tag as it should be shown
     * @param color     optional hex colour, {@code null} for none
     * @return the created tag
     */
    public InventoryTag create(int stationId, String name, String color) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_tag(station_id, name, color, position)
                VALUES (:station_id, :name, :color,
                        coalesce((SELECT max(position) + 1 FROM inventory_tag WHERE station_id = :station_id), 0))
                RETURNING %s;""",
                call().bind("station_id", stationId).bind("name", name).bind("color", color),
                InventoryTag.map(),
                INVENTORY_TAG_COLUMNS);
    }

    /**
     * Finds a tag by its identifier.
     */
    public Optional<InventoryTag> findById(int id) {
        return SqlSupport.findById("inventory_tag", INVENTORY_TAG_COLUMNS, id, InventoryTag.map());
    }

    /**
     * Finds the station's tag matching a name, comparing it in the merged form so a tag typed
     * {@code " Funk "} finds the one stored as {@code Funk}.
     */
    public Optional<InventoryTag> findByName(int stationId, String name) {
        return query("""
                SELECT %s
                FROM inventory_tag
                WHERE station_id = :station_id AND canonical_name = :canonical;""", INVENTORY_TAG_COLUMNS)
                .single(call().bind("station_id", stationId).bind("canonical", InventoryTag.canonical(name)))
                .map(InventoryTag.map())
                .first();
    }

    /**
     * Every tag a station has, in the order it put them in.
     */
    public List<InventoryTag> findByStation(int stationId) {
        return query("""
                SELECT %s
                FROM inventory_tag
                WHERE station_id = :station_id
                ORDER BY position, name;""", INVENTORY_TAG_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(InventoryTag.map())
                .all();
    }

    /**
     * Renames a tag and changes its colour and place.
     */
    public boolean update(int id, String name, String color, int position) {
        return query("""
                UPDATE inventory_tag
                SET name     = :name,
                    color    = :color,
                    position = :position
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("color", color)
                        .bind("position", position))
                .update()
                .changed();
    }

    /**
     * Deletes a tag, but only when it belongs to the given station.
     */
    public boolean delete(int id, int stationId) {
        return SqlSupport.deleteByIdInStation("inventory_tag", id, stationId);
    }

    /**
     * The tags one item carries.
     */
    public List<InventoryTag> findTagsForItem(int itemId) {
        return query("""
                SELECT %s
                FROM inventory_tag t
                         JOIN inventory_item_tag it ON it.tag_id = t.id
                WHERE it.item_id = :item_id
                ORDER BY t.position, t.name;""", SqlSupport.alias("t", INVENTORY_TAG_COLUMNS))
                .single(call().bind("item_id", itemId))
                .map(InventoryTag.map())
                .all();
    }

    /**
     * The tags a whole list of items carries, in one round trip, because a request per row is most
     * of what a page of two hundred costs.
     *
     * @param itemIds the items
     * @return item id to its tags, holding only the items that carry one
     */
    public Map<Integer, List<InventoryTag>> findTagsForItems(Collection<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return Map.of();
        Map<Integer, List<InventoryTag>> tags = new HashMap<>();
        for (var entry : query("""
                SELECT it.item_id, %s
                FROM inventory_tag t
                         JOIN inventory_item_tag it ON it.tag_id = t.id
                WHERE it.item_id = ANY (:item_ids)
                ORDER BY t.position, t.name;""", SqlSupport.alias("t", INVENTORY_TAG_COLUMNS))
                .single(call().bind("item_ids", List.copyOf(itemIds), PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("item_id"), InventoryTag.map().map(row)))
                .all()) {
            tags.computeIfAbsent(entry.getKey(), id -> new ArrayList<>()).add(entry.getValue());
        }
        return tags;
    }

    /**
     * Replaces the tags an item carries, ignoring tag ids that belong to another station.
     */
    public void setItemTags(int itemId, int stationId, List<Integer> tagIds) {
        query("DELETE FROM inventory_item_tag WHERE item_id = :item_id;")
                .single(call().bind("item_id", itemId))
                .delete();
        if (tagIds == null || tagIds.isEmpty()) return;
        query("""
                INSERT INTO inventory_item_tag(item_id, tag_id)
                SELECT :item_id, t.id
                FROM inventory_tag t
                WHERE t.id = ANY (:tag_ids) AND t.station_id = :station_id
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("item_id", itemId)
                        .bind("tag_ids", List.copyOf(tagIds), PostgreSqlTypes.INTEGER)
                        .bind("station_id", stationId))
                .insert();
    }

    /**
     * The items carrying a tag of this name at any of the named stations, across every inventory
     * those stations keep. Names are matched in their merged form, so a station spelling the tag
     * differently still contributes its items.
     *
     * @param stationIds the stations to search
     * @param name       the tag name as somebody typed it
     * @return the items found, the holding station's own first by name
     */
    public List<TaggedItemSummary> findItemsByTag(Collection<Integer> stationIds, String name) {
        if (stationIds == null || stationIds.isEmpty()) return List.of();
        return query(TAGGED_ITEM_SELECT + """
                        WHERE t.canonical_name = :canonical
                          AND inv.station_id = ANY (:station_ids)
                        ORDER BY s.name, inv.name, i.name, i.id;""")
                .single(call().bind("canonical", InventoryTag.canonical(name))
                        .bind("station_ids", List.copyOf(stationIds), PostgreSqlTypes.INTEGER))
                .map(TaggedItemSummary.map())
                .all();
    }

    /**
     * Every piece of one station carrying a tag, with the inventory and kind each sits in, so that
     * what a partner may see can be decided against the station's offer rather than guessed here.
     *
     * <p>This answers nothing about sharing on purpose. The offer is one decision and it lives in
     * one place; a second rule written here would be a second way to the same gear, and the two
     * would drift.
     *
     * @param stationId the station serving the request
     * @param name      the tag name as the asking station typed it
     * @return every tagged piece, whether or not it is offered
     */
    public List<TaggedItemSummary> findTaggedItemsOfStation(int stationId, String name) {
        return query(TAGGED_ITEM_SELECT + """
                        WHERE t.canonical_name = :canonical
                          AND inv.station_id = :station_id
                        ORDER BY inv.name, i.name, i.id;""")
                .single(call().bind("canonical", InventoryTag.canonical(name))
                        .bind("station_id", stationId))
                .map(TaggedItemSummary.map())
                .all();
    }

    /**
     * How many items carry each of a station's tags, so the tag list can say what it is worth.
     *
     * @param stationId the station
     * @return tag id to the number of items carrying it, holding only the tags that are used
     */
    public Map<Integer, Integer> countItemsPerTag(int stationId) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (var entry : query("""
                SELECT t.id AS tag_id, count(it.item_id) AS item_count
                FROM inventory_tag t
                         JOIN inventory_item_tag it ON it.tag_id = t.id
                WHERE t.station_id = :station_id
                GROUP BY t.id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> Map.entry(row.getInt("tag_id"), row.getInt("item_count")))
                .all()) {
            counts.put(entry.getKey(), entry.getValue());
        }
        return counts;
    }
}
