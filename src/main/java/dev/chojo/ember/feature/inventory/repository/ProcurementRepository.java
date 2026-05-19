/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.inventory.entity.Procurement;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class ProcurementRepository {

    public Procurement create(int stationId, int inventoryId, int memberId, Integer sizeId, String notes) {
        return Query.query("""
                            INSERT INTO equipment_procurement(station_id, inventory_id, member_id, size_id, notes)
                            VALUES(:station_id, :inventory_id, :member_id, :size_id, :notes)
                            RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("inventory_id", inventoryId)
                        .bind("member_id", memberId)
                        .bind("size_id", sizeId)
                        .bind("notes", notes != null ? notes : ""))
                .map(Procurement.map())
                .first()
                .orElseThrow();
    }

    public Optional<Procurement> findById(int id) {
        return Query.query("SELECT * FROM equipment_procurement WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(Procurement.map())
                .first();
    }

    public List<Procurement> findByStation(int stationId) {
        return Query.query(
                        "SELECT * FROM equipment_procurement WHERE station_id = :station_id ORDER BY requested_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(Procurement.map())
                .all();
    }

    public List<Procurement> findOpen(int stationId) {
        return Query.query(
                        "SELECT * FROM equipment_procurement WHERE station_id = :station_id AND fulfilled_at IS NULL ORDER BY requested_at ASC;")
                .single(Call.of().bind("station_id", stationId))
                .map(Procurement.map())
                .all();
    }

    public boolean fulfill(int id) {
        return Query.query("UPDATE equipment_procurement SET fulfilled_at = :fulfilled_at WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("fulfilled_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM equipment_procurement WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }
}
