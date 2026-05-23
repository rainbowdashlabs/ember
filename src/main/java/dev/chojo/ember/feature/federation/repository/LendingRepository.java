/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.federation.entity.InventoryBlock;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access for inventory lending requests, messages, and blocks.
 */
@Singleton
public class LendingRepository {

    // -- Lending Requests --

    public LendingRequest createRequest(
            int requestingStationId, int owningStationId, LocalDate dateFrom, LocalDate dateTo, int createdBy) {
        return Query.query("""
                        INSERT INTO federation_lending_request(requesting_station_id, owning_station_id, status, requested_date_from, requested_date_to, created_by)
                        VALUES (:requesting_station_id, :owning_station_id, :status, :date_from, :date_to, :created_by)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("requesting_station_id", requestingStationId)
                        .bind("owning_station_id", owningStationId)
                        .bind("status", LendingStatus.REQUESTED.name())
                        .bind("date_from", dateFrom)
                        .bind("date_to", dateTo)
                        .bind("created_by", createdBy))
                .map(LendingRequest.map())
                .first()
                .orElseThrow();
    }

    public Optional<LendingRequest> findRequestById(int id) {
        return Query.query("SELECT * FROM federation_lending_request WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(LendingRequest.map())
                .first();
    }

    public List<LendingRequest> findRequestsByStation(int stationId) {
        return Query.query(
                        "SELECT * FROM federation_lending_request WHERE requesting_station_id = :station_id OR owning_station_id = :station_id ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(LendingRequest.map())
                .all();
    }

    public boolean updateRequestStatus(int id, LendingStatus status) {
        return Query.query("UPDATE federation_lending_request SET status = :status, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("status", status.name()))
                .update()
                .changed();
    }

    // -- Lending Request Items --

    public LendingRequestItem addRequestItem(int requestId, Integer inventoryId, Integer itemId, int quantity) {
        return Query.query("""
                        INSERT INTO federation_lending_request_item(request_id, inventory_id, item_id, quantity)
                        VALUES (:request_id, :inventory_id, :item_id, :quantity)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("request_id", requestId)
                        .bind("inventory_id", inventoryId)
                        .bind("item_id", itemId)
                        .bind("quantity", quantity))
                .map(LendingRequestItem.map())
                .first()
                .orElseThrow();
    }

    public List<LendingRequestItem> findItemsByRequest(int requestId) {
        return Query.query("SELECT * FROM federation_lending_request_item WHERE request_id = :request_id ORDER BY id;")
                .single(Call.of().bind("request_id", requestId))
                .map(LendingRequestItem.map())
                .all();
    }

    public boolean assignItem(int requestItemId, int assignedItemId) {
        return Query.query(
                        "UPDATE federation_lending_request_item SET assigned_item_id = :assigned_item_id WHERE id = :id;")
                .single(Call.of().bind("id", requestItemId).bind("assigned_item_id", assignedItemId))
                .update()
                .changed();
    }

    // -- Messages --

    public LendingMessage createMessage(
            int requestId, int senderStationId, Integer senderMemberId, String message, boolean isSystem) {
        return Query.query("""
                        INSERT INTO federation_lending_message(request_id, sender_station_id, sender_member_id, message, is_system)
                        VALUES (:request_id, :sender_station_id, :sender_member_id, :message, :is_system)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("request_id", requestId)
                        .bind("sender_station_id", senderStationId)
                        .bind("sender_member_id", senderMemberId)
                        .bind("message", message)
                        .bind("is_system", isSystem))
                .map(LendingMessage.map())
                .first()
                .orElseThrow();
    }

    public List<LendingMessage> findMessagesByRequest(int requestId) {
        return Query.query(
                        "SELECT * FROM federation_lending_message WHERE request_id = :request_id ORDER BY created_at;")
                .single(Call.of().bind("request_id", requestId))
                .map(LendingMessage.map())
                .all();
    }

    /**
     * Returns only messages sent by the given station for a lending request.
     * Used for distributed message storage where each station stores only its own messages.
     */
    public List<LendingMessage> findLocalMessages(int requestId, int stationId) {
        return Query.query(
                        "SELECT * FROM federation_lending_message WHERE request_id = :request_id AND sender_station_id = :station_id ORDER BY created_at;")
                .single(Call.of().bind("request_id", requestId).bind("station_id", stationId))
                .map(LendingMessage.map())
                .all();
    }

    // -- Inventory Blocks --

    public InventoryBlock createBlock(
            int stationId, Integer inventoryId, Integer itemId, LocalDate blockFrom, LocalDate blockTo, String reason) {
        return Query.query("""
                        INSERT INTO federation_inventory_block(station_id, inventory_id, item_id, block_from, block_to, reason)
                        VALUES (:station_id, :inventory_id, :item_id, :block_from, :block_to, :reason)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("inventory_id", inventoryId)
                        .bind("item_id", itemId)
                        .bind("block_from", blockFrom)
                        .bind("block_to", blockTo)
                        .bind("reason", reason))
                .map(InventoryBlock.map())
                .first()
                .orElseThrow();
    }

    public List<InventoryBlock> findBlocksByStation(int stationId) {
        return Query.query(
                        "SELECT * FROM federation_inventory_block WHERE station_id = :station_id ORDER BY block_from;")
                .single(Call.of().bind("station_id", stationId))
                .map(InventoryBlock.map())
                .all();
    }

    public boolean deleteBlock(int id) {
        return Query.query("DELETE FROM federation_inventory_block WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public List<InventoryBlock> findActiveBlocks(int stationId, LocalDate dateFrom, LocalDate dateTo) {
        return Query.query("""
                        SELECT * FROM federation_inventory_block
                        WHERE station_id = :station_id
                          AND block_from <= :date_to AND block_to >= :date_from
                        ORDER BY block_from;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("date_from", dateFrom)
                        .bind("date_to", dateTo))
                .map(InventoryBlock.map())
                .all();
    }

    public boolean isBlocked(int stationId, Integer inventoryId, Integer itemId, LocalDate dateFrom, LocalDate dateTo) {
        var blocks = findActiveBlocks(stationId, dateFrom, dateTo);
        for (var block : blocks) {
            // Station-wide block (no inventory or item specified)
            if (block.inventoryId() == null && block.itemId() == null) {
                return true;
            }
            // Inventory-level block
            if (inventoryId != null
                    && block.inventoryId() != null
                    && block.inventoryId().equals(inventoryId)
                    && block.itemId() == null) {
                return true;
            }
            // Item-level block
            if (itemId != null && block.itemId() != null && block.itemId().equals(itemId)) {
                return true;
            }
        }
        return false;
    }
}
