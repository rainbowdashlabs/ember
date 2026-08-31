/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A piece that actually went out for one evening of an appointment.
 *
 * <p>The only claim on stock that is written down. Everything still merely planned is derived from
 * the recurrence rule when somebody asks, which is why there is no horizon to choose, no job to refill
 * one, and nothing to clean up when a series is thinned: a series that no longer produces an evening
 * no longer produces its claim either. So the database holds a record of what happened and computes
 * what is planned, which is the right way round.
 *
 * @param needId     the line the piece was handed over against
 * @param eventDate  the evening it went out for
 * @param itemId     the piece
 * @param claimFrom  when it left
 * @param claimTo    when it is due back
 * @param handedBy   the member who recorded it, or {@code null}
 * @param handedAt   when the handover was recorded
 * @param returnedAt when it came back, or {@code null} while it is still out
 */
public record EquipmentHandover(
        int id,
        int needId,
        LocalDate eventDate,
        int itemId,
        Instant claimFrom,
        Instant claimTo,
        Integer handedBy,
        Instant handedAt,
        Instant returnedAt) {

    /**
     * Whether the piece is still out.
     *
     * @return {@code true} while it has not come back
     */
    public boolean outstanding() {
        return returnedAt == null;
    }

    /**
     * Creates a row mapping for database result set conversion.
     *
     * @return the mapping
     */
    public static RowMapping<EquipmentHandover> map() {
        return row -> new EquipmentHandover(
                row.getInt("id"),
                row.getInt("need_id"),
                row.getObject("event_date", LocalDate.class),
                row.getInt("item_id"),
                row.get("claim_from", INSTANT_TIMESTAMP),
                row.get("claim_to", INSTANT_TIMESTAMP),
                row.getObject("handed_by", Integer.class),
                row.get("handed_at", INSTANT_TIMESTAMP),
                row.get("returned_at", INSTANT_TIMESTAMP));
    }
}
