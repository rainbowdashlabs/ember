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

/**
 * Repository for managing equipment procurement requests.
 */
@Singleton
public class ProcurementRepository {

    /**
     * Creates a new procurement request.
     *
     * @param stationId   the station ID
     * @param inventoryId the inventory the equipment is from
     * @param memberId    the member the equipment is being procured for
     * @param sizeId      the requested size, or {@code null} if not applicable
     * @param notes       additional notes
     * @return the created procurement
     */
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

    /**
     * Finds a procurement request by its ID.
     *
     * @param id the procurement ID
     * @return the procurement, or empty if not found
     */
    public Optional<Procurement> findById(int id) {
        return Query.query("SELECT * FROM equipment_procurement WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(Procurement.map())
                .first();
    }

    /**
     * Finds all procurement requests for a station, ordered by most recent first.
     *
     * @param stationId the station ID
     * @return list of procurements
     */
    public List<Procurement> findByStation(int stationId) {
        return Query.query(
                        "SELECT * FROM equipment_procurement WHERE station_id = :station_id ORDER BY requested_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(Procurement.map())
                .all();
    }

    /**
     * Finds open (unfulfilled) procurement requests for a station, ordered by oldest first.
     *
     * @param stationId the station ID
     * @return list of open procurements
     */
    public List<Procurement> findOpen(int stationId) {
        return Query.query(
                        "SELECT * FROM equipment_procurement WHERE station_id = :station_id AND fulfilled_at IS NULL ORDER BY requested_at ASC;")
                .single(Call.of().bind("station_id", stationId))
                .map(Procurement.map())
                .all();
    }

    /**
     * Marks a procurement request as fulfilled by setting the fulfilled timestamp.
     *
     * @param id the procurement ID
     * @return {@code true} if the procurement was updated
     */
    public boolean fulfill(int id) {
        return Query.query("UPDATE equipment_procurement SET fulfilled_at = :fulfilled_at WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("fulfilled_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * Deletes a procurement request by its ID.
     *
     * @param id the procurement ID
     * @return {@code true} if the procurement was deleted
     */
    public boolean delete(int id) {
        return Query.query("DELETE FROM equipment_procurement WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }
}
