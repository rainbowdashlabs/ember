/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.inventory.entity.ExchangeLog;
import dev.chojo.ember.feature.inventory.entity.ExchangeRequest;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for managing equipment exchange requests and their status change logs.
 */
@Singleton
public class ExchangeRepository {
    private static final String EXCHANGE_REQUEST_COLUMNS =
            "id, station_id, member_id, item_id, inventory_id, old_size_id, new_size_id, exchanged_item_id, status, reason, created_at, updated_at, created_by";
    private static final String EXCHANGE_LOG_COLUMNS =
            "id, request_id, old_status, new_status, changed_by, changed_at, note";

    /**
     * Creates a new exchange request.
     *
     * @param stationId   the station ID
     * @param memberId    the member requesting the exchange
     * @param itemId      the current item being exchanged, or {@code null}
     * @param inventoryId the inventory the exchange is for
     * @param oldSizeId   the current size, or {@code null}
     * @param newSizeId   the desired new size, or {@code null}
     * @param reason      the reason for the exchange
     * @param createdBy   the member who created the request on behalf of another, or {@code null}
     * @return the created exchange request
     */
    public ExchangeRequest create(
            int stationId,
            int memberId,
            Integer itemId,
            int inventoryId,
            Integer oldSizeId,
            Integer newSizeId,
            String reason,
            Integer createdBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO equipment_exchange_request(station_id, member_id, item_id, inventory_id, old_size_id, new_size_id, reason, created_by)
                VALUES(:station_id, :member_id, :item_id, :inventory_id, :old_size_id, :new_size_id, :reason, :created_by)
                RETURNING %s;""".formatted(EXCHANGE_REQUEST_COLUMNS),
                call().bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("item_id", itemId)
                        .bind("inventory_id", inventoryId)
                        .bind("old_size_id", oldSizeId)
                        .bind("new_size_id", newSizeId)
                        .bind("reason", reason != null ? reason : "")
                        .bind("created_by", createdBy),
                ExchangeRequest.map());
    }

    /**
     * Finds an exchange request by its ID.
     *
     * @param id the exchange request ID
     * @return the exchange request, or empty if not found
     */
    public Optional<ExchangeRequest> findById(int id) {
        return SqlSupport.findById("equipment_exchange_request", EXCHANGE_REQUEST_COLUMNS, id, ExchangeRequest.map());
    }

    /**
     * Finds all exchange requests for a station, ordered by most recent first.
     *
     * @param stationId the station ID
     * @return list of exchange requests
     */
    public List<ExchangeRequest> findByStation(int stationId) {
        return query("""
                SELECT %s FROM equipment_exchange_request WHERE station_id = :station_id ORDER BY created_at DESC;""", EXCHANGE_REQUEST_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(ExchangeRequest.map())
                .all();
    }

    /**
     * Finds all exchange requests for a specific member, ordered by most recent first.
     *
     * @param memberId the member ID
     * @return list of exchange requests
     */
    public List<ExchangeRequest> findByMember(int memberId) {
        return query("""
                SELECT %s FROM equipment_exchange_request WHERE member_id = :member_id ORDER BY created_at DESC;""", EXCHANGE_REQUEST_COLUMNS)
                .single(call().bind("member_id", memberId))
                .map(ExchangeRequest.map())
                .all();
    }

    /**
     * Updates the status of an exchange request.
     *
     * @param id     the exchange request ID
     * @param status the new status
     * @return {@code true} if the status was updated
     */
    public boolean updateStatus(int id, ExchangeStatus status) {
        return query("UPDATE equipment_exchange_request SET status = :status, updated_at = :updated_at WHERE id = :id;")
                .single(call().bind("id", id)
                        .bind("status", status)
                        .bind("updated_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * Updates the status of an exchange request and records the exchanged replacement item.
     *
     * @param id              the exchange request ID
     * @param status          the new status
     * @param exchangedItemId the replacement item ID
     * @return {@code true} if the status was updated
     */
    public boolean updateStatusWithExchangedItem(int id, ExchangeStatus status, Integer exchangedItemId) {
        return query(
                        "UPDATE equipment_exchange_request SET status = :status, exchanged_item_id = :exchanged_item_id, updated_at = :updated_at WHERE id = :id;")
                .single(call().bind("id", id)
                        .bind("status", status)
                        .bind("exchanged_item_id", exchangedItemId)
                        .bind("updated_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * Creates a status change log entry for an exchange request.
     *
     * @param requestId the exchange request ID
     * @param oldStatus the previous status
     * @param newStatus the new status
     * @param changedBy the member who made the change
     * @param note      an optional note
     * @return the created log entry
     */
    public ExchangeLog createLog(
            int requestId, ExchangeStatus oldStatus, ExchangeStatus newStatus, int changedBy, String note) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO equipment_exchange_log(request_id, old_status, new_status, changed_by, note)
                VALUES(:request_id, :old_status, :new_status, :changed_by, :note)
                RETURNING %s;""".formatted(EXCHANGE_LOG_COLUMNS),
                call().bind("request_id", requestId)
                        .bind("old_status", oldStatus)
                        .bind("new_status", newStatus)
                        .bind("changed_by", changedBy)
                        .bind("note", note != null ? note : ""),
                ExchangeLog.map());
    }

    /**
     * Finds all status change logs for an exchange request, ordered chronologically.
     *
     * @param requestId the exchange request ID
     * @return list of log entries
     */
    public List<ExchangeLog> findLogs(int requestId) {
        return query("""
                SELECT %s FROM equipment_exchange_log WHERE request_id = :request_id ORDER BY changed_at ASC;""", EXCHANGE_LOG_COLUMNS)
                .single(call().bind("request_id", requestId))
                .map(ExchangeLog.map())
                .all();
    }

    public int countPendingByStation(int stationId) {
        return SqlSupport.count(
                "SELECT count(*) FROM equipment_exchange_request WHERE station_id = :station_id AND status != :done;",
                call().bind("station_id", stationId).bind("done", ExchangeStatus.DONE));
    }

    /**
     * Deletes an exchange request by its ID.
     *
     * @param id the exchange request ID
     * @return {@code true} if the request was deleted
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("equipment_exchange_request", id);
    }
}
