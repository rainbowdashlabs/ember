/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.federation.entity.InventoryBlock;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.util.sql.SqlSupport.count;
import static dev.chojo.ember.util.sql.SqlSupport.findById;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

/**
 * Data access for inventory lending requests, messages, and blocks. Peer references on the
 * lending tables are stored as {@code uuid} (the station's stable cross-instance identity);
 * inventory and station_member references stay as local integer FKs because they are by
 * definition local to whichever instance hosts the row.
 */
@Singleton
public class LendingRepository {
    private static final String LENDING_REQUEST_COLUMNS = """
            id, requesting_station_uid, owning_station_uid, status, requested_date_from, \
            requested_date_to, created_by, created_at, updated_at, event_id, event_date, occasion""";
    private static final String LENDING_REQUEST_ITEM_COLUMNS =
            "id, request_id, inventory_id, item_id, art_id, quantity, need_id";
    private static final String LENDING_MESSAGE_COLUMNS =
            "id, request_id, sender_station_uid, sender_member_id, message, is_system, created_at";
    private static final String INVENTORY_BLOCK_COLUMNS =
            "id, station_id, inventory_id, item_id, block_from, block_to, reason";

    // -- Lending Requests --

    public LendingRequest createRequest(
            UUID requestingStationUid,
            UUID owningStationUid,
            LocalDate dateFrom,
            LocalDate dateTo,
            int createdBy,
            Integer eventId,
            LocalDate eventDate,
            String occasion) {
        return insertReturning(
                """
                INSERT INTO federation_lending_request(requesting_station_uid, owning_station_uid, status, requested_date_from, requested_date_to, created_by, event_id, event_date, occasion)
                VALUES (:requesting_station_uid::uuid, :owning_station_uid::uuid, :status, :date_from, :date_to, :created_by, :event_id, :event_date, :occasion)
                RETURNING %s;""",
                call().bind("requesting_station_uid", requestingStationUid, StandardValueConverter.UUID_STRING)
                        .bind("owning_station_uid", owningStationUid, StandardValueConverter.UUID_STRING)
                        .bind("status", LendingStatus.REQUESTED)
                        .bind("date_from", dateFrom)
                        .bind("date_to", dateTo)
                        .bind("created_by", createdBy)
                        .bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("occasion", occasion == null ? "" : occasion),
                LendingRequest.map(),
                LENDING_REQUEST_COLUMNS);
    }

    /**
     * The open requests one appointment has sent, which cancelling it has to withdraw.
     *
     * @param eventId the appointment
     * @return the requests that have not been settled yet
     */
    public List<LendingRequest> findOpenRequestsForEvent(int eventId) {
        return query("""
                SELECT %s FROM federation_lending_request
                WHERE event_id = :event_id AND status IN ('REQUESTED', 'APPROVED')
                ORDER BY id;""", LENDING_REQUEST_COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(LendingRequest.map())
                .all();
    }

    public Optional<LendingRequest> findRequestById(int id) {
        return findById("federation_lending_request", LENDING_REQUEST_COLUMNS, id, LendingRequest.map());
    }

    public List<LendingRequest> findRequestsByStation(UUID stationUid) {
        return query("""
                SELECT %s
                FROM
                    federation_lending_request
                WHERE requesting_station_uid = :station_uid::uuid
                   OR owning_station_uid = :station_uid::uuid
                ORDER BY created_at DESC;""", LENDING_REQUEST_COLUMNS)
                .single(call().bind("station_uid", stationUid, StandardValueConverter.UUID_STRING))
                .map(LendingRequest.map())
                .all();
    }

    public boolean updateRequestStatus(int id, LendingStatus status) {
        return query("UPDATE federation_lending_request SET status = :status, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("status", status))
                .update()
                .changed();
    }

    // -- Lending Request Items --

    public LendingRequestItem addRequestItem(
            int requestId, Integer inventoryId, Integer itemId, Integer artId, int quantity, Integer needId) {
        return insertReturning(
                """
                INSERT INTO federation_lending_request_item(request_id, inventory_id, item_id, art_id, quantity, need_id)
                VALUES (:request_id, :inventory_id, :item_id, :art_id, :quantity, :need_id)
                RETURNING %s;""",
                call().bind("request_id", requestId)
                        .bind("inventory_id", inventoryId)
                        .bind("item_id", itemId)
                        .bind("art_id", artId)
                        .bind("quantity", quantity)
                        .bind("need_id", needId),
                LendingRequestItem.map(),
                LENDING_REQUEST_ITEM_COLUMNS);
    }

    public List<LendingRequestItem> findItemsByRequest(int requestId) {
        return query(
                        "SELECT %s FROM federation_lending_request_item WHERE request_id = :request_id ORDER BY id;",
                        LENDING_REQUEST_ITEM_COLUMNS)
                .single(call().bind("request_id", requestId))
                .map(LendingRequestItem.map())
                .all();
    }

    /**
     * Sets one more piece aside for a line.
     *
     * <p>Adding rather than replacing, which is the whole reason the assignment left the request line:
     * a line asking for four blue radios is answered with four of them, and a single column could only
     * ever hold the last one written.
     *
     * <p>Setting the same piece aside twice is not an error and reports success, because what the
     * caller asked for is true afterwards either way.
     *
     * @param requestItemId  the line
     * @param assignedItemId the piece
     * @return {@code true} when the piece is set aside for that line
     */
    public boolean assignItem(int requestItemId, int assignedItemId) {
        return query("""
                INSERT INTO federation_lending_request_item_assignment(request_item_id, item_id)
                VALUES (:request_item_id, :item_id)
                ON CONFLICT (request_item_id, item_id) DO UPDATE SET item_id = excluded.item_id;""")
                .single(call().bind("request_item_id", requestItemId).bind("item_id", assignedItemId))
                .insert()
                .changed();
    }

    /**
     * The pieces set aside for one line.
     *
     * @param requestItemId the line
     * @return the piece IDs, in a stable order
     */
    public List<Integer> findAssignedItems(int requestItemId) {
        return query("""
                SELECT item_id FROM federation_lending_request_item_assignment
                WHERE request_item_id = :request_item_id ORDER BY item_id;""")
                .single(call().bind("request_item_id", requestItemId))
                .map(row -> row.getInt("item_id"))
                .all();
    }

    /**
     * Takes a piece back out of a line.
     *
     * @param requestItemId  the line
     * @param assignedItemId the piece
     * @return {@code true} if a row went
     */
    public boolean unassignItem(int requestItemId, int assignedItemId) {
        return query("""
                DELETE FROM federation_lending_request_item_assignment
                WHERE request_item_id = :request_item_id AND item_id = :item_id;""")
                .single(call().bind("request_item_id", requestItemId).bind("item_id", assignedItemId))
                .delete()
                .changed();
    }

    public List<LentOutItem> findLentOutByInventory(int inventoryId, UUID owningStationUid) {
        return query("""
                SELECT
                    ri.id                 AS request_item_id,
                    r.id                  AS request_id,
                    ri.item_id,
                    ri.quantity,
                    a.item_id             AS assigned_item_id,
                    r.status,
                    r.requested_date_from AS date_from,
                    r.requested_date_to   AS date_to,
                    s.name                AS requesting_station_name
                FROM
                    federation_lending_request_item ri
                        JOIN federation_lending_request r
                        ON r.id = ri.request_id
                        LEFT JOIN federation_lending_request_item_assignment a
                        ON a.request_item_id = ri.id
                        LEFT JOIN station s
                        ON s.uid = r.requesting_station_uid
                WHERE ri.inventory_id = :inventory_id
                  AND r.owning_station_uid = :owning_station_uid::uuid
                  AND r.status IN ('APPROVED', 'LENT')
                ORDER BY r.requested_date_to ASC NULLS LAST;""")
                .single(call().bind("inventory_id", inventoryId)
                        .bind("owning_station_uid", owningStationUid, StandardValueConverter.UUID_STRING))
                .map(LentOutItem.map())
                .all();
    }

    public LendingMessage createMessage(
            int requestId, UUID senderStationUid, Integer senderMemberId, String message, boolean isSystem) {
        return insertReturning(
                """
                INSERT INTO federation_lending_message(request_id, sender_station_uid, sender_member_id, message, is_system)
                VALUES (:request_id, :sender_station_uid::uuid, :sender_member_id, :message, :is_system)
                RETURNING %s;""",
                call().bind("request_id", requestId)
                        .bind("sender_station_uid", senderStationUid, StandardValueConverter.UUID_STRING)
                        .bind("sender_member_id", senderMemberId)
                        .bind("message", message)
                        .bind("is_system", isSystem),
                LendingMessage.map(),
                LENDING_MESSAGE_COLUMNS);
    }

    // -- Messages --

    public List<LendingMessage> findMessagesByRequest(int requestId) {
        return query(
                        "SELECT %s FROM federation_lending_message WHERE request_id = :request_id ORDER BY created_at;",
                        LENDING_MESSAGE_COLUMNS)
                .single(call().bind("request_id", requestId))
                .map(LendingMessage.map())
                .all();
    }

    /**
     * Returns only messages sent by the given station for a lending request.
     * Used for distributed message storage where each station stores only its own messages.
     */
    public List<LendingMessage> findLocalMessages(int requestId, UUID stationUid) {
        return query("""
                SELECT %s
                FROM
                    federation_lending_message
                WHERE request_id = :request_id
                  AND sender_station_uid = :station_uid::uuid
                ORDER BY created_at;""", LENDING_MESSAGE_COLUMNS)
                .single(call().bind("request_id", requestId)
                        .bind("station_uid", stationUid, StandardValueConverter.UUID_STRING))
                .map(LendingMessage.map())
                .all();
    }

    public InventoryBlock createBlock(
            int stationId, Integer inventoryId, Integer itemId, LocalDate blockFrom, LocalDate blockTo, String reason) {
        return insertReturning(
                """
                INSERT INTO federation_inventory_block(station_id, inventory_id, item_id, block_from, block_to, reason)
                VALUES (:station_id, :inventory_id, :item_id, :block_from, :block_to, :reason)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("inventory_id", inventoryId)
                        .bind("item_id", itemId)
                        .bind("block_from", blockFrom)
                        .bind("block_to", blockTo)
                        .bind("reason", reason),
                InventoryBlock.map(),
                INVENTORY_BLOCK_COLUMNS);
    }

    // -- Inventory Blocks --

    public List<InventoryBlock> findBlocksByStation(int stationId) {
        return query(
                        "SELECT %s FROM federation_inventory_block WHERE station_id = :station_id ORDER BY block_from;",
                        INVENTORY_BLOCK_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(InventoryBlock.map())
                .all();
    }

    /**
     * Deletes a block of the given station. The station is part of the statement rather than
     * checked by the caller, so a block belonging to another station cannot be deleted however
     * this is called: a block is what holds equipment back from a partner, and one deleted
     * silently reopens the owning station's inventory.
     */
    public boolean deleteBlock(int id, int stationId) {
        return query("DELETE FROM federation_inventory_block WHERE id = :id AND station_id = :station_id;")
                .single(call().bind("id", id).bind("station_id", stationId))
                .delete()
                .changed();
    }

    public List<InventoryBlock> findActiveBlocks(int stationId, LocalDate dateFrom, LocalDate dateTo) {
        return query("""
                SELECT %s FROM federation_inventory_block
                WHERE station_id = :station_id
                  AND block_from <= :date_to AND block_to >= :date_from
                ORDER BY block_from;""", INVENTORY_BLOCK_COLUMNS)
                .single(call().bind("station_id", stationId)
                        .bind("date_from", dateFrom)
                        .bind("date_to", dateTo))
                .map(InventoryBlock.map())
                .all();
    }

    public boolean isBlocked(int stationId, Integer inventoryId, Integer itemId, LocalDate dateFrom, LocalDate dateTo) {
        var blocks = findActiveBlocks(stationId, dateFrom, dateTo);
        for (var block : blocks) {
            if (block.inventoryId() == null && block.itemId() == null) {
                return true;
            }
            if (block.inventoryId() != null && block.inventoryId().equals(inventoryId) && block.itemId() == null) {
                return true;
            }
            if (block.itemId() != null && block.itemId().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    public int countActionableRequests(UUID stationUid) {
        return count("""
                SELECT count(*) AS cnt FROM federation_lending_request
                WHERE owning_station_uid = :station_uid::uuid AND status = 'REQUESTED';""", call().bind("station_uid", stationUid, StandardValueConverter.UUID_STRING));
    }

    /**
     * Finds items from lending requests that are currently lent out (APPROVED or LENT status)
     * for a specific inventory, owned by a specific station.
     */
    public record LentOutItem(
            int requestItemId,
            int requestId,
            Integer itemId,
            int quantity,
            Integer assignedItemId,
            String status,
            LocalDate dateFrom,
            LocalDate dateTo,
            String requestingStationName) {
        public static RowMapping<LentOutItem> map() {
            return row -> new LentOutItem(
                    row.getInt("request_item_id"),
                    row.getInt("request_id"),
                    row.getObject("item_id", Integer.class),
                    row.getInt("quantity"),
                    row.getObject("assigned_item_id", Integer.class),
                    row.getString("status"),
                    row.getObject("date_from", LocalDate.class),
                    row.getObject("date_to", LocalDate.class),
                    row.getString("requesting_station_name"));
        }
    }
}
