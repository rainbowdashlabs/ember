/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemHistory;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventorySummary;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for managing inventories, their items, sizes, history entries, and requirements.
 */
@Singleton
public class InventoryRepository {

    // -- Inventory --

    /**
     * Finds an inventory by its ID.
     *
     * @param id the inventory ID
     * @return the inventory, or empty if not found
     */
    public Optional<Inventory> findById(int id) {
        return query("SELECT id, station_id, name, inventory_type, has_sizes FROM inventory WHERE id = :id;")
                .single(call().bind("id", id))
                .map(Inventory.map())
                .first();
    }

    /**
     * Finds all inventories belonging to a station.
     *
     * @param stationId the station ID
     * @return list of inventories for the station
     */
    public List<Inventory> findByStation(int stationId) {
        return query(
                        "SELECT id, station_id, name, inventory_type, has_sizes FROM inventory WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(Inventory.map())
                .all();
    }

    public List<InventorySummary> findSummariesByStation(int stationId) {
        return query("""
                SELECT i.id, i.station_id, i.name, i.inventory_type, i.has_sizes,
                       coalesce(counts.item_count, 0) AS item_count,
                       coalesce(counts.lost_count, 0) AS lost_count,
                       coalesce(proc.procurement_count, 0) AS procurement_count,
                       coalesce(lent.lent_out_count, 0) AS lent_out_count
                FROM inventory i
                LEFT JOIN (
                    SELECT inventory_id,
                           count(*) AS item_count,
                           count(*) FILTER (WHERE lost_at IS NOT NULL) AS lost_count
                    FROM inventory_item
                    GROUP BY inventory_id
                ) counts ON counts.inventory_id = i.id
                LEFT JOIN (
                    SELECT inventory_id,
                           count(*) AS procurement_count
                    FROM equipment_procurement
                    WHERE fulfilled_at IS NULL
                    GROUP BY inventory_id
                ) proc ON proc.inventory_id = i.id
                LEFT JOIN (
                    SELECT li.inventory_id,
                           count(*) FILTER (WHERE li.assigned_item_id IS NOT NULL) AS lent_out_count
                    FROM federation_lending_request_item li
                    JOIN federation_lending_request lr ON lr.id = li.request_id
                    WHERE lr.status IN ('APPROVED', 'LENT')
                    GROUP BY li.inventory_id
                ) lent ON lent.inventory_id = i.id
                WHERE i.station_id = :station_id
                ORDER BY i.name;""")
                .single(call().bind("station_id", stationId))
                .map(InventorySummary.map())
                .all();
    }

    /**
     * Creates a new inventory for a station.
     *
     * @param stationId     the station ID
     * @param name          the inventory name
     * @param inventoryType the inventory type
     * @param hasSizes      whether the inventory supports sizes
     * @return the created inventory
     */
    public Inventory create(int stationId, String name, InventoryType inventoryType, boolean hasSizes) {
        return query(
                        "INSERT INTO inventory(station_id, name, inventory_type, has_sizes) VALUES(:station_id, :name, :inventory_type, :has_sizes) RETURNING id, station_id, name, inventory_type, has_sizes;")
                .single(call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("inventory_type", inventoryType)
                        .bind("has_sizes", hasSizes))
                .map(Inventory.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates an existing inventory.
     *
     * @param id            the inventory ID
     * @param name          the new name
     * @param inventoryType the new inventory type
     * @param hasSizes      whether the inventory supports sizes
     * @return {@code true} if the inventory was updated
     */
    public boolean update(int id, String name, InventoryType inventoryType, boolean hasSizes) {
        return query(
                        "UPDATE inventory SET name = :name, inventory_type = :inventory_type, has_sizes = :has_sizes WHERE id = :id;")
                .single(call().bind("name", name)
                        .bind("inventory_type", inventoryType)
                        .bind("has_sizes", hasSizes)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an inventory by its ID.
     *
     * @param id the inventory ID
     * @return {@code true} if the inventory was deleted
     */
    public boolean delete(int id) {
        return query("DELETE FROM inventory WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    // -- Sizes --

    /**
     * Finds all sizes for an inventory, ordered by position.
     *
     * @param inventoryId the inventory ID
     * @return list of sizes
     */
    public List<InventorySize> findSizes(int inventoryId) {
        return query(
                        "SELECT id, inventory_id, label, position, note FROM inventory_size WHERE inventory_id = :inventory_id ORDER BY position;")
                .single(call().bind("inventory_id", inventoryId))
                .map(InventorySize.map())
                .all();
    }

    /**
     * Creates a new size for an inventory.
     *
     * @param inventoryId the inventory ID
     * @param label       the size label
     * @param position    the sort position
     * @param note        an optional note
     * @return the insertion result
     */
    public InsertionResult createSize(int inventoryId, String label, int position, String note) {
        return query(
                        "INSERT INTO inventory_size(inventory_id, label, position, note) VALUES(:inventory_id, :label, :position, :note);")
                .single(call().bind("inventory_id", inventoryId)
                        .bind("label", label)
                        .bind("position", position)
                        .bind("note", note != null ? note : ""))
                .insert();
    }

    /**
     * Updates an existing inventory size.
     *
     * @param id       the size ID
     * @param label    the new label
     * @param position the new position
     * @param note     the new note
     * @return {@code true} if the size was updated
     */
    public boolean updateSize(int id, String label, int position, String note) {
        return query("UPDATE inventory_size SET label = :label, position = :position, note = :note WHERE id = :id;")
                .single(call().bind("label", label)
                        .bind("position", position)
                        .bind("note", note != null ? note : "")
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an inventory size by its ID.
     *
     * @param id the size ID
     * @return {@code true} if the size was deleted
     */
    public boolean deleteSize(int id) {
        return query("DELETE FROM inventory_size WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    // -- Items --

    /**
     * Finds an inventory item by its ID.
     *
     * @param id the item ID
     * @return the item, or empty if not found
     */
    public Optional<InventoryItem> findItemById(int id) {
        return query("SELECT * FROM inventory_item WHERE id = :id;")
                .single(call().bind("id", id))
                .map(InventoryItem.map())
                .first();
    }

    public Optional<InventoryItem> findByInternalId(int stationId, String internalId) {
        return query("""
                SELECT ii.* FROM inventory_item ii
                JOIN inventory i ON i.id = ii.inventory_id
                WHERE i.station_id = :station_id AND ii.internal_id = :internal_id
                LIMIT 1;""")
                .single(call().bind("station_id", stationId).bind("internal_id", internalId))
                .map(InventoryItem.map())
                .first();
    }

    public List<InventoryItem> findFreeItems(int inventoryId, LocalDate dateFrom, LocalDate dateTo) {
        return query("""
                SELECT * FROM inventory_item
                WHERE inventory_id = :inventory_id
                  AND assigned_to IS NULL
                  AND lost_at IS NULL
                  AND id NOT IN (
                      SELECT li.assigned_item_id FROM federation_lending_request_item li
                      JOIN federation_lending_request lr ON lr.id = li.request_id
                      WHERE li.assigned_item_id IS NOT NULL
                        AND lr.status IN ('APPROVED', 'LENT')
                        AND lr.requested_date_from <= :date_to
                        AND (lr.requested_date_to IS NULL OR lr.requested_date_to >= :date_from)
                  )
                ORDER BY id;""")
                .single(call().bind("inventory_id", inventoryId)
                        .bind("date_from", dateFrom)
                        .bind("date_to", dateTo))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * Finds all items in an inventory.
     *
     * @param inventoryId the inventory ID
     * @return list of items
     */
    public List<InventoryItem> findItems(int inventoryId) {
        return query("SELECT * FROM inventory_item WHERE inventory_id = :inventory_id;")
                .single(call().bind("inventory_id", inventoryId))
                .map(InventoryItem.map())
                .all();
    }

    public List<InventoryItem> findItemsByStation(int stationId) {
        return query("""
                SELECT ii.* FROM inventory_item ii
                JOIN inventory i ON i.id = ii.inventory_id
                WHERE i.station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(InventoryItem.map())
                .all();
    }

    public List<InventorySize> findSizesByStation(int stationId) {
        return query("""
                SELECT s.id, s.inventory_id, s.label, s.position, s.note FROM inventory_size s
                JOIN inventory i ON i.id = s.inventory_id
                WHERE i.station_id = :station_id ORDER BY s.position;""")
                .single(call().bind("station_id", stationId))
                .map(InventorySize.map())
                .all();
    }

    public List<InventoryItem> findItemsByMember(int memberId) {
        return query("SELECT * FROM inventory_item WHERE assigned_to = :member_id;")
                .single(call().bind("member_id", memberId))
                .map(InventoryItem.map())
                .all();
    }

    public int countItemsByMember(int memberId) {
        return query("SELECT count(*) FROM inventory_item WHERE assigned_to = :member_id;")
                .single(call().bind("member_id", memberId))
                .map(row -> row.getInt(1))
                .first()
                .orElse(0);
    }

    /**
     * Finds all unassigned and non-lost items in an inventory, ordered by name.
     *
     * @param inventoryId the inventory ID
     * @return list of available items
     */
    public List<InventoryItem> findUnassignedItems(int inventoryId) {
        return query(
                        "SELECT * FROM inventory_item WHERE inventory_id = :inventory_id AND assigned_to IS NULL AND lost_at IS NULL ORDER BY name;")
                .single(call().bind("inventory_id", inventoryId))
                .map(InventoryItem.map())
                .all();
    }

    /**
     * Creates a new inventory item with the default item source.
     *
     * @param inventoryId the inventory ID
     * @param internalId  the internal identifier
     * @param name        the item name
     * @param sizeId      the size ID, or {@code null}
     * @param metadata    JSON metadata
     * @return the created item
     */
    public InventoryItem createItem(
            int inventoryId, String internalId, String name, Integer sizeId, InventoryItemMetadata metadata) {
        return createItem(inventoryId, internalId, name, sizeId, metadata, null);
    }

    /**
     * Creates a new inventory item with a specified item source.
     *
     * @param inventoryId the inventory ID
     * @param internalId  the internal identifier
     * @param name        the item name
     * @param sizeId      the size ID, or {@code null}
     * @param metadata    JSON metadata
     * @param itemSource  the item source (internal or external), or {@code null} for default
     * @return the created item
     */
    public InventoryItem createItem(
            int inventoryId,
            String internalId,
            String name,
            Integer sizeId,
            InventoryItemMetadata metadata,
            InventoryItem.ItemSource itemSource) {
        return query("""
                INSERT INTO inventory_item(inventory_id, internal_id, name, size_id, metadata, item_source)
                VALUES (:inventory_id, :internal_id, :name, :size_id, :metadata::JSONB, :item_source)
                RETURNING *;""")
                .single(call().bind("inventory_id", inventoryId)
                        .bind("internal_id", internalId)
                        .bind("name", name)
                        .bind("size_id", sizeId)
                        .bind("metadata", (metadata != null ? metadata : InventoryItemMetadata.empty()).toJson())
                        .bind("item_source", itemSource))
                .map(InventoryItem.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates an existing inventory item.
     *
     * @param id         the item ID
     * @param internalId the new internal identifier
     * @param name       the new name
     * @param sizeId     the new size ID, or {@code null}
     * @param metadata   the new JSON metadata
     * @return {@code true} if the item was updated
     */
    public boolean updateItem(int id, String internalId, String name, Integer sizeId, InventoryItemMetadata metadata) {
        return query("""
                UPDATE inventory_item
                SET
                    internal_id = :internal_id,
                    name        = :name,
                    size_id     = :size_id,
                    metadata    = :metadata::JSONB
                WHERE id = :id;""")
                .single(call().bind("internal_id", internalId)
                        .bind("name", name)
                        .bind("size_id", sizeId)
                        .bind("metadata", (metadata != null ? metadata : InventoryItemMetadata.empty()).toJson())
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Assigns an item to a member, or unassigns it by passing {@code null}.
     * Assigning an item to a member also clears the item's container — an
     * item handed to a member is no longer in storage.
     *
     * @param itemId   the item ID
     * @param memberId the member ID to assign to, or {@code null} to unassign
     * @return {@code true} if the assignment was updated
     */
    public boolean assignItem(int itemId, Integer memberId) {
        return query("""
                UPDATE inventory_item
                SET assigned_to = :member_id,
                    container_id = CASE WHEN :member_id::int IS NOT NULL THEN NULL ELSE container_id END
                WHERE id = :id;""")
                .single(call().bind("member_id", memberId).bind("id", itemId))
                .update()
                .changed();
    }

    /**
     * Sets or clears the container an item is physically placed in. Placing
     * an item in a container also clears the assignment — an item back in
     * storage is no longer with a member.
     *
     * @param itemId      the item ID
     * @param containerId the container ID, or {@code null} to clear the location
     * @return {@code true} if the item row was updated
     */
    public boolean setItemContainer(int itemId, Integer containerId) {
        return query("""
                UPDATE inventory_item
                SET container_id = :container_id,
                    assigned_to = CASE WHEN :container_id::int IS NOT NULL THEN NULL ELSE assigned_to END
                WHERE id = :id;""")
                .single(call().bind("container_id", containerId).bind("id", itemId))
                .update()
                .changed();
    }

    /**
     * Returns whether the given station has any item whose internal id matches.
     */
    public boolean itemInternalIdExists(int stationId, String internalId, Integer excludeItemId) {
        return query("""
                SELECT 1 FROM inventory_item ii
                JOIN inventory i ON i.id = ii.inventory_id
                WHERE i.station_id = :station_id
                  AND ii.internal_id = :internal_id
                  AND (:exclude_id::int IS NULL OR ii.id <> :exclude_id)
                LIMIT 1;""")
                .single(call().bind("station_id", stationId)
                        .bind("internal_id", internalId)
                        .bind("exclude_id", excludeItemId))
                .map(row -> row.getInt(1))
                .first()
                .isPresent();
    }

    /**
     * Marks an inventory item as lost by setting the lost timestamp.
     *
     * @param id the item ID
     * @return {@code true} if the item was marked as lost
     */
    public boolean markLost(int id) {
        return query("UPDATE inventory_item SET lost_at = now() WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    /**
     * Marks a previously lost inventory item as found by clearing the lost timestamp.
     *
     * @param id the item ID
     * @return {@code true} if the item was marked as found
     */
    public boolean markFound(int id) {
        return query("UPDATE inventory_item SET lost_at = NULL WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an inventory item by its ID.
     *
     * @param id the item ID
     * @return {@code true} if the item was deleted
     */
    public boolean deleteItem(int id) {
        return query("DELETE FROM inventory_item WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    // -- History --

    /**
     * Finds the assignment history for an item, ordered by most recent first.
     *
     * @param itemId the item ID
     * @return list of history entries
     */
    public List<InventoryItemHistory> findHistory(int itemId) {
        return query(
                        "SELECT id, item_id, member_id, member_name, given_out, returned FROM inventory_item_history WHERE item_id = :itemId ORDER BY given_out DESC;")
                .single(call().bind("itemId", itemId))
                .map(InventoryItemHistory.map())
                .all();
    }

    /**
     * Creates a new history entry for an item assignment with the current timestamp.
     *
     * @param itemId     the item ID
     * @param memberId   the member the item is assigned to
     * @param memberName the member's name at the time of assignment
     * @return the created history entry
     */
    public InventoryItemHistory createHistory(int itemId, int memberId, String memberName) {
        return query(
                        "INSERT INTO inventory_item_history(item_id, member_id, member_name) VALUES(:itemId, :memberId, :memberName) RETURNING id, item_id, member_id, member_name, given_out, returned;")
                .single(call().bind("itemId", itemId).bind("memberId", memberId).bind("memberName", memberName))
                .map(InventoryItemHistory.map())
                .first()
                .orElseThrow();
    }

    /**
     * Creates a history entry with explicit given-out and returned timestamps, used for data import.
     *
     * @param itemId     the item ID
     * @param memberId   the member ID
     * @param memberName the member's name
     * @param givenOut   when the item was given out
     * @param returned   when the item was returned
     */
    public void createHistoryWithDates(
            int itemId, int memberId, String memberName, Instant givenOut, Instant returned) {
        query("""
                INSERT INTO inventory_item_history(item_id, member_id, member_name, given_out, returned)
                VALUES(:itemId, :memberId, :memberName, :givenOut, :returned);""")
                .single(call().bind("itemId", itemId)
                        .bind("memberId", memberId)
                        .bind("memberName", memberName)
                        .bind("givenOut", givenOut, INSTANT_TIMESTAMP)
                        .bind("returned", returned, INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * Marks the open history entry for an item and member as returned by setting the returned timestamp.
     *
     * @param itemId   the item ID
     * @param memberId the member ID
     * @return {@code true} if a history entry was updated
     */
    public boolean returnHistory(int itemId, int memberId) {
        return query(
                        "UPDATE inventory_item_history SET returned = now() WHERE item_id = :itemId AND member_id = :memberId AND returned IS NULL;")
                .single(call().bind("itemId", itemId).bind("memberId", memberId))
                .update()
                .changed();
    }

    // -- Requirements --

    /**
     * Finds all inventory requirements for a station, ordered by position.
     *
     * @param stationId the station ID
     * @return list of requirements
     */
    public List<InventoryRequirement> findAllRequirementsByStation(int stationId) {
        return query(
                        "SELECT r.id, r.inventory_id, r.user_type, r.group_id, r.quantity, r.position FROM inventory_requirement r JOIN inventory i ON r.inventory_id = i.id WHERE i.station_id = :stationId ORDER BY r.position, r.id;")
                .single(call().bind("stationId", stationId))
                .map(InventoryRequirement.map())
                .all();
    }

    /**
     * Creates a new inventory requirement for a role or group.
     *
     * @param inventoryId the inventory ID
     * @param userType    the user type name, or {@code null} for no user type restriction
     * @param groupId     the group ID (0 means no group restriction)
     * @param quantity    the required quantity
     * @return the created requirement
     */
    public InventoryRequirement createRequirement(
            int inventoryId, StationUserType userType, int groupId, int quantity) {
        return query(
                        "INSERT INTO inventory_requirement(inventory_id, user_type, group_id, quantity) VALUES(:inventoryId, :userType, :groupId, :quantity) RETURNING id, inventory_id, user_type, group_id, quantity, position;")
                .single(call().bind("inventoryId", inventoryId)
                        .bind("userType", userType)
                        .bind("groupId", groupId == 0 ? null : groupId)
                        .bind("quantity", quantity))
                .map(InventoryRequirement.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates the quantity of an inventory requirement.
     *
     * @param id       the requirement ID
     * @param quantity the new quantity
     * @return {@code true} if the requirement was updated
     */
    public boolean updateRequirement(int id, int quantity) {
        return query("UPDATE inventory_requirement SET quantity = :quantity WHERE id = :id;")
                .single(call().bind("quantity", quantity).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Updates the display position of an inventory requirement.
     *
     * @param id       the requirement ID
     * @param position the new position
     * @return {@code true} if the requirement was updated
     */
    public boolean updateRequirementPosition(int id, int position) {
        return query("UPDATE inventory_requirement SET position = :position WHERE id = :id;")
                .single(call().bind("position", position).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an inventory requirement by its ID.
     *
     * @param id the requirement ID
     * @return {@code true} if the requirement was deleted
     */
    public boolean deleteRequirement(int id) {
        return query("DELETE FROM inventory_requirement WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }
}
