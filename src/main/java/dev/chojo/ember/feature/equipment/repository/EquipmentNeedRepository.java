/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.repository;

import dev.chojo.ember.feature.equipment.entity.EquipmentHandover;
import dev.chojo.ember.feature.equipment.entity.EquipmentNeed;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The lines of what an appointment needs, and the pieces that actually went out against them.
 */
@Singleton
public class EquipmentNeedRepository {

    private static final String NEED_COLUMNS = """
            id, event_id, event_date, item_id, art_id, inventory_id, quantity, lead_minutes, \
            trail_minutes, position""";
    private static final String HANDOVER_COLUMNS = """
            id, need_id, event_date, item_id, claim_from, claim_to, handed_by, handed_at, returned_at""";

    /**
     * Finds a line by its ID.
     *
     * @param id the line ID
     * @return the line, or empty if not found
     */
    public Optional<EquipmentNeed> findById(int id) {
        return SqlSupport.findById("event_equipment_need", NEED_COLUMNS, id, EquipmentNeed.map());
    }

    /**
     * Every line of one appointment, the standing ones and the ones written for a single evening.
     *
     * @param eventId the appointment
     * @return the lines, in their own order
     */
    public List<EquipmentNeed> findByEvent(int eventId) {
        return query(
                        "SELECT %s FROM event_equipment_need WHERE event_id = :event_id ORDER BY position, id;",
                        NEED_COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(EquipmentNeed.map())
                .all();
    }

    /**
     * Every line of every appointment of one station.
     *
     * <p>What the availability of a station's gear has to be read against: a claim is written on an
     * appointment, and any appointment of the station may hold the piece being asked about.
     *
     * @param stationId the station
     * @return the lines
     */
    public List<EquipmentNeed> findByStation(int stationId) {
        return query("""
                SELECT %s FROM event_equipment_need n
                JOIN station_event e ON e.id = n.event_id
                WHERE e.station_id = :station_id
                ORDER BY n.event_id, n.position, n.id;""", SqlSupport.alias("n", NEED_COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(EquipmentNeed.map())
                .all();
    }

    /**
     * Appends a line to an appointment.
     *
     * @param eventId      the appointment
     * @param eventDate    the one evening it speaks for, or {@code null} for the whole series
     * @param itemId       the named piece, or {@code null}
     * @param artId        the kind of thing, or {@code null}
     * @param inventoryId  the inventory, or {@code null}
     * @param quantity     how many pieces
     * @param leadMinutes  how long before the appointment the gear goes
     * @param trailMinutes how long after it the gear is back
     * @return the created line
     */
    public EquipmentNeed create(
            int eventId,
            LocalDate eventDate,
            Integer itemId,
            Integer artId,
            Integer inventoryId,
            int quantity,
            int leadMinutes,
            int trailMinutes) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_equipment_need(event_id, event_date, item_id, art_id, inventory_id,
                                                 quantity, lead_minutes, trail_minutes, position)
                VALUES (:event_id, :event_date, :item_id, :art_id, :inventory_id, :quantity,
                        :lead_minutes, :trail_minutes,
                        (SELECT coalesce(max(position) + 1, 0) FROM event_equipment_need
                          WHERE event_id = :event_id))
                RETURNING %s;""",
                call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("item_id", itemId)
                        .bind("art_id", artId)
                        .bind("inventory_id", inventoryId)
                        .bind("quantity", quantity)
                        .bind("lead_minutes", leadMinutes)
                        .bind("trail_minutes", trailMinutes),
                EquipmentNeed.map(),
                NEED_COLUMNS);
    }

    /**
     * Rewrites what a line asks for and when the gear is away.
     *
     * @param id           the line ID
     * @param quantity     the new count
     * @param leadMinutes  the new lead
     * @param trailMinutes the new trail
     * @return {@code true} if a row changed
     */
    public boolean update(int id, int quantity, int leadMinutes, int trailMinutes) {
        return query("""
                UPDATE event_equipment_need
                SET quantity = :quantity, lead_minutes = :lead_minutes, trail_minutes = :trail_minutes
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("quantity", quantity)
                        .bind("lead_minutes", leadMinutes)
                        .bind("trail_minutes", trailMinutes))
                .update()
                .changed();
    }

    /**
     * Rewrites the order of an appointment's lines.
     *
     * @param eventId    the appointment
     * @param orderedIds the line IDs in their new order
     */
    public void reorder(int eventId, List<Integer> orderedIds) {
        SqlSupport.reorder("event_equipment_need", "position", "event_id", eventId, orderedIds);
    }

    /**
     * Deletes a line.
     *
     * @param id the line ID
     * @return {@code true} if a row went
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("event_equipment_need", id);
    }

    /**
     * Deletes every line of an appointment.
     *
     * <p>What releases the claims of a cancelled or deleted appointment. It runs in the service write
     * path rather than in an event handler, because dispatch is synchronous and handler exceptions are
     * swallowed, so a release that failed would fail silently.
     *
     * @param eventId the appointment
     * @return how many lines went
     */
    public int deleteByEvent(int eventId) {
        return query("DELETE FROM event_equipment_need WHERE event_id = :event_id;")
                .single(call().bind("event_id", eventId))
                .delete()
                .rows();
    }

    /**
     * Deletes the lines an evening wrote for itself, leaving the series alone.
     *
     * @param eventId the appointment
     * @param date    the evening
     * @return how many lines went
     */
    public int deleteForDate(int eventId, LocalDate date) {
        return query("DELETE FROM event_equipment_need WHERE event_id = :event_id AND event_date = :event_date;")
                .single(call().bind("event_id", eventId).bind("event_date", date))
                .delete()
                .rows();
    }

    /**
     * Records that a piece went out for one evening.
     *
     * @param needId    the line it went out against
     * @param eventDate the evening
     * @param itemId    the piece
     * @param claimFrom when it left
     * @param claimTo   when it is due back
     * @param handedBy  the member recording it, or {@code null}
     * @return the recorded handover
     */
    public EquipmentHandover recordHandover(
            int needId, LocalDate eventDate, int itemId, Instant claimFrom, Instant claimTo, Integer handedBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_equipment_handover(need_id, event_date, item_id, claim_from, claim_to, handed_by)
                VALUES (:need_id, :event_date, :item_id, :claim_from, :claim_to, :handed_by)
                ON CONFLICT (need_id, event_date, item_id)
                    DO UPDATE SET claim_from = :claim_from, claim_to = :claim_to, returned_at = NULL
                RETURNING %s;""",
                call().bind("need_id", needId)
                        .bind("event_date", eventDate)
                        .bind("item_id", itemId)
                        .bind("claim_from", claimFrom, INSTANT_TIMESTAMP)
                        .bind("claim_to", claimTo, INSTANT_TIMESTAMP)
                        .bind("handed_by", handedBy),
                EquipmentHandover.map(),
                HANDOVER_COLUMNS);
    }

    /**
     * Marks a piece as back, which ends its claim whatever its window still says.
     *
     * @param id the handover
     * @return {@code true} if a row changed
     */
    public boolean markReturned(int id) {
        return query("UPDATE event_equipment_handover SET returned_at = now() WHERE id = :id AND returned_at IS NULL;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    /**
     * The pieces that went out for one evening of one appointment.
     *
     * @param eventId the appointment
     * @param date    the evening
     * @return the handovers
     */
    public List<EquipmentHandover> findHandovers(int eventId, LocalDate date) {
        return query("""
                SELECT %s FROM event_equipment_handover h
                JOIN event_equipment_need n ON n.id = h.need_id
                WHERE n.event_id = :event_id AND h.event_date = :event_date
                ORDER BY h.id;""", SqlSupport.alias("h", HANDOVER_COLUMNS))
                .single(call().bind("event_id", eventId).bind("event_date", date))
                .map(EquipmentHandover.map())
                .all();
    }

    /**
     * Every piece of a station that is still out on an appointment and overlaps a window.
     *
     * <p>A returned piece claims nothing, so it never appears here however far its window reaches.
     *
     * @param stationId the station
     * @param from      the first moment of the window
     * @param to        the last moment of the window
     * @return the handovers, each naming its line and its piece
     */
    public List<EquipmentHandover> findOpenHandovers(int stationId, Instant from, Instant to) {
        return query("""
                SELECT %s FROM event_equipment_handover h
                JOIN event_equipment_need n ON n.id = h.need_id
                JOIN station_event e ON e.id = n.event_id
                WHERE e.station_id = :station_id
                  AND h.returned_at IS NULL
                  AND h.claim_from < :window_to
                  AND h.claim_to > :window_from
                ORDER BY h.id;""", SqlSupport.alias("h", HANDOVER_COLUMNS))
                .single(call().bind("station_id", stationId)
                        .bind("window_from", from, INSTANT_TIMESTAMP)
                        .bind("window_to", to, INSTANT_TIMESTAMP))
                .map(EquipmentHandover.map())
                .all();
    }
}
