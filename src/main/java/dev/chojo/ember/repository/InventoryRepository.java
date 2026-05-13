/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.entity.Inventory;
import dev.chojo.ember.entity.InventoryItem;
import dev.chojo.ember.entity.InventoryItemHistory;
import dev.chojo.ember.entity.InventoryRequirement;
import dev.chojo.ember.entity.InventorySize;
import dev.chojo.ember.entity.InventoryType;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class InventoryRepository {

    // -- Inventory --

    public Optional<Inventory> findById(int id) {
        return Query.query("SELECT id, station_id, name, inventory_type, has_sizes FROM inventory WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(Inventory.map())
                .first();
    }

    public List<Inventory> findByStation(int stationId) {
        return Query.query(
                        "SELECT id, station_id, name, inventory_type, has_sizes FROM inventory WHERE station_id = :station_id;")
                .single(Call.of().bind("station_id", stationId))
                .map(Inventory.map())
                .all();
    }

    public Inventory create(int stationId, String name, InventoryType inventoryType, boolean hasSizes) {
        return Query.query(
                        "INSERT INTO inventory(station_id, name, inventory_type, has_sizes) VALUES(:station_id, :name, :inventory_type, :has_sizes) RETURNING id, station_id, name, inventory_type, has_sizes;")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("inventory_type", inventoryType)
                        .bind("has_sizes", hasSizes))
                .map(Inventory.map())
                .first()
                .orElseThrow();
    }

    public boolean update(int id, String name, InventoryType inventoryType, boolean hasSizes) {
        return Query.query(
                        "UPDATE inventory SET name = :name, inventory_type = :inventory_type, has_sizes = :has_sizes WHERE id = :id;")
                .single(Call.of()
                        .bind("name", name)
                        .bind("inventory_type", inventoryType)
                        .bind("has_sizes", hasSizes)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM inventory WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Sizes --

    public List<InventorySize> findSizes(int inventoryId) {
        return Query.query(
                        "SELECT id, inventory_id, label, position, note FROM inventory_size WHERE inventory_id = :inventory_id ORDER BY position;")
                .single(Call.of().bind("inventory_id", inventoryId))
                .map(InventorySize.map())
                .all();
    }

    public InsertionResult createSize(int inventoryId, String label, int position, String note) {
        return Query.query(
                        "INSERT INTO inventory_size(inventory_id, label, position, note) VALUES(:inventory_id, :label, :position, :note);")
                .single(Call.of()
                        .bind("inventory_id", inventoryId)
                        .bind("label", label)
                        .bind("position", position)
                        .bind("note", note != null ? note : ""))
                .insert();
    }

    public boolean updateSize(int id, String label, int position, String note) {
        return Query.query(
                        "UPDATE inventory_size SET label = :label, position = :position, note = :note WHERE id = :id;")
                .single(Call.of()
                        .bind("label", label)
                        .bind("position", position)
                        .bind("note", note != null ? note : "")
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteSize(int id) {
        return Query.query("DELETE FROM inventory_size WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Items --

    public Optional<InventoryItem> findItemById(int id) {
        return Query.query("SELECT * FROM inventory_item WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(InventoryItem.map())
                .first();
    }

    public List<InventoryItem> findItems(int inventoryId) {
        return Query.query("SELECT * FROM inventory_item WHERE inventory_id = :inventory_id;")
                .single(Call.of().bind("inventory_id", inventoryId))
                .map(InventoryItem.map())
                .all();
    }

    public List<InventoryItem> findItemsByMember(int memberId) {
        return Query.query("SELECT * FROM inventory_item WHERE assigned_to = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .map(InventoryItem.map())
                .all();
    }

    public List<InventoryItem> findUnassignedItems(int inventoryId) {
        return Query.query(
                        "SELECT * FROM inventory_item WHERE inventory_id = :inventory_id AND assigned_to IS NULL AND lost_at IS NULL ORDER BY name;")
                .single(Call.of().bind("inventory_id", inventoryId))
                .map(InventoryItem.map())
                .all();
    }

    public InventoryItem createItem(int inventoryId, String internalId, String name, Integer sizeId, String metadata) {
        return createItem(inventoryId, internalId, name, sizeId, metadata, null);
    }

    public InventoryItem createItem(
            int inventoryId, String internalId, String name, Integer sizeId, String metadata, String itemSource) {
        return Query.query("""
                            INSERT INTO inventory_item(inventory_id, internal_id, name, size_id, metadata, item_source)
                            VALUES (:inventory_id, :internal_id, :name, :size_id, :metadata::JSONB, :item_source)
                            RETURNING *;""")
                .single(Call.of()
                        .bind("inventory_id", inventoryId)
                        .bind("internal_id", internalId)
                        .bind("name", name)
                        .bind("size_id", sizeId)
                        .bind("metadata", metadata != null ? metadata : "{}")
                        .bind("item_source", itemSource))
                .map(InventoryItem.map())
                .first()
                .orElseThrow();
    }

    public boolean updateItem(int id, String internalId, String name, Integer sizeId, String metadata) {
        return Query.query("""
                            UPDATE inventory_item
                            SET
                                internal_id = :internal_id,
                                name        = :name,
                                size_id     = :size_id,
                                metadata    = :metadata::JSONB
                            WHERE id = :id;""")
                .single(Call.of()
                        .bind("internal_id", internalId)
                        .bind("name", name)
                        .bind("size_id", sizeId)
                        .bind("metadata", metadata)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean assignItem(int itemId, Integer memberId) {
        return Query.query("UPDATE inventory_item SET assigned_to = :member_id WHERE id = :id;")
                .single(Call.of().bind("member_id", memberId).bind("id", itemId))
                .update()
                .changed();
    }

    public boolean markLost(int id) {
        return Query.query("UPDATE inventory_item SET lost_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public boolean markFound(int id) {
        return Query.query("UPDATE inventory_item SET lost_at = NULL WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteItem(int id) {
        return Query.query("DELETE FROM inventory_item WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- History --

    public List<InventoryItemHistory> findHistory(int itemId) {
        return Query.query(
                        "SELECT id, item_id, member_id, member_name, given_out, returned FROM inventory_item_history WHERE item_id = :itemId ORDER BY given_out DESC;")
                .single(Call.of().bind("itemId", itemId))
                .map(InventoryItemHistory.map())
                .all();
    }

    public InventoryItemHistory createHistory(int itemId, int memberId, String memberName) {
        return Query.query(
                        "INSERT INTO inventory_item_history(item_id, member_id, member_name) VALUES(:itemId, :memberId, :memberName) RETURNING id, item_id, member_id, member_name, given_out, returned;")
                .single(Call.of()
                        .bind("itemId", itemId)
                        .bind("memberId", memberId)
                        .bind("memberName", memberName))
                .map(InventoryItemHistory.map())
                .first()
                .orElseThrow();
    }

    public void createHistoryWithDates(
            int itemId, int memberId, String memberName, Instant givenOut, Instant returned) {
        Query.query("""
                        INSERT INTO inventory_item_history(item_id, member_id, member_name, given_out, returned)
                        VALUES(:itemId, :memberId, :memberName, :givenOut, :returned);""")
                .single(Call.of()
                        .bind("itemId", itemId)
                        .bind("memberId", memberId)
                        .bind("memberName", memberName)
                        .bind("givenOut", givenOut, INSTANT_TIMESTAMP)
                        .bind("returned", returned, INSTANT_TIMESTAMP))
                .insert();
    }

    public boolean returnHistory(int itemId, int memberId) {
        return Query.query(
                        "UPDATE inventory_item_history SET returned = now() WHERE item_id = :itemId AND member_id = :memberId AND returned IS NULL;")
                .single(Call.of().bind("itemId", itemId).bind("memberId", memberId))
                .update()
                .changed();
    }

    // -- Requirements --

    public List<InventoryRequirement> findAllRequirementsByStation(int stationId) {
        return Query.query(
                        "SELECT r.id, r.inventory_id, r.role_id, r.group_id, r.quantity, r.position FROM inventory_requirement r JOIN inventory i ON r.inventory_id = i.id WHERE i.station_id = :stationId ORDER BY r.position, r.id;")
                .single(Call.of().bind("stationId", stationId))
                .map(InventoryRequirement.map())
                .all();
    }

    public InventoryRequirement createRequirement(int inventoryId, int roleId, int groupId, int quantity) {
        return Query.query(
                        "INSERT INTO inventory_requirement(inventory_id, role_id, group_id, quantity) VALUES(:inventoryId, :roleId, :groupId, :quantity) RETURNING id, inventory_id, role_id, group_id, quantity, position;")
                .single(Call.of()
                        .bind("inventoryId", inventoryId)
                        .bind("roleId", roleId == 0 ? null : roleId)
                        .bind("groupId", groupId == 0 ? null : groupId)
                        .bind("quantity", quantity))
                .map(InventoryRequirement.map())
                .first()
                .orElseThrow();
    }

    public boolean updateRequirement(int id, int quantity) {
        return Query.query("UPDATE inventory_requirement SET quantity = :quantity WHERE id = :id;")
                .single(Call.of().bind("quantity", quantity).bind("id", id))
                .update()
                .changed();
    }

    public boolean updateRequirementPosition(int id, int position) {
        return Query.query("UPDATE inventory_requirement SET position = :position WHERE id = :id;")
                .single(Call.of().bind("position", position).bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteRequirement(int id) {
        return Query.query("DELETE FROM inventory_requirement WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }
}
