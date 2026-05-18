/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.ExchangeLog;
import dev.chojo.ember.entity.ExchangeRequest;
import dev.chojo.ember.entity.ExchangeStatus;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class ExchangeRepository {

    public ExchangeRequest create(
            int stationId,
            int memberId,
            Integer itemId,
            int inventoryId,
            Integer oldSizeId,
            Integer newSizeId,
            String reason,
            Integer createdBy) {
        return Query.query("""
                            INSERT INTO equipment_exchange_request(station_id, member_id, item_id, inventory_id, old_size_id, new_size_id, reason, created_by)
                            VALUES(:station_id, :member_id, :item_id, :inventory_id, :old_size_id, :new_size_id, :reason, :created_by)
                            RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("item_id", itemId)
                        .bind("inventory_id", inventoryId)
                        .bind("old_size_id", oldSizeId)
                        .bind("new_size_id", newSizeId)
                        .bind("reason", reason != null ? reason : "")
                        .bind("created_by", createdBy))
                .map(ExchangeRequest.map())
                .first()
                .orElseThrow();
    }

    public Optional<ExchangeRequest> findById(int id) {
        return Query.query("SELECT * FROM equipment_exchange_request WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(ExchangeRequest.map())
                .first();
    }

    public List<ExchangeRequest> findByStation(int stationId) {
        return Query.query(
                        "SELECT * FROM equipment_exchange_request WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(ExchangeRequest.map())
                .all();
    }

    public List<ExchangeRequest> findByMember(int memberId) {
        return Query.query(
                        "SELECT * FROM equipment_exchange_request WHERE member_id = :member_id ORDER BY created_at DESC;")
                .single(Call.of().bind("member_id", memberId))
                .map(ExchangeRequest.map())
                .all();
    }

    public boolean updateStatus(int id, ExchangeStatus status) {
        return Query.query(
                        "UPDATE equipment_exchange_request SET status = :status, updated_at = :updated_at WHERE id = :id;")
                .single(Call.of()
                        .bind("id", id)
                        .bind("status", status)
                        .bind("updated_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public boolean updateStatusWithExchangedItem(int id, ExchangeStatus status, Integer exchangedItemId) {
        return Query.query(
                        "UPDATE equipment_exchange_request SET status = :status, exchanged_item_id = :exchanged_item_id, updated_at = :updated_at WHERE id = :id;")
                .single(Call.of()
                        .bind("id", id)
                        .bind("status", status)
                        .bind("exchanged_item_id", exchangedItemId)
                        .bind("updated_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public ExchangeLog createLog(
            int requestId, ExchangeStatus oldStatus, ExchangeStatus newStatus, int changedBy, String note) {
        return Query.query("""
                            INSERT INTO equipment_exchange_log(request_id, old_status, new_status, changed_by, note)
                            VALUES(:request_id, :old_status, :new_status, :changed_by, :note)
                            RETURNING *;""")
                .single(Call.of()
                        .bind("request_id", requestId)
                        .bind("old_status", oldStatus)
                        .bind("new_status", newStatus)
                        .bind("changed_by", changedBy)
                        .bind("note", note != null ? note : ""))
                .map(ExchangeLog.map())
                .first()
                .orElseThrow();
    }

    public List<ExchangeLog> findLogs(int requestId) {
        return Query.query(
                        "SELECT * FROM equipment_exchange_log WHERE request_id = :request_id ORDER BY changed_at ASC;")
                .single(Call.of().bind("request_id", requestId))
                .map(ExchangeLog.map())
                .all();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM equipment_exchange_request WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }
}
