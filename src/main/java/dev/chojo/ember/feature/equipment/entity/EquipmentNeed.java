/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.inventory.entity.LineTarget;

import java.time.Duration;
import java.time.LocalDate;

/**
 * One line of what an appointment needs.
 *
 * <p>It carries the same three targets a collection line carries, and two more things a list of
 * equipment cannot do without. The lead and the trail say how long the gear is gone either side of
 * the appointment, because the period the equipment is away is not the period the appointment lasts:
 * the radios are fetched the evening before and come back on the Monday, and a request asking only
 * for the Saturday leaves the owner finding an empty shelf on the Friday.
 *
 * <p>A line with no date holds for every evening the series produces, which is why a weekly Dienst is
 * written once rather than fifty times. A line with a date speaks for that evening alone: it is added
 * to the standing list, and where it names the same thing as a standing line it takes its place.
 *
 * @param eventId      the appointment the line belongs to
 * @param eventDate    the one evening this line speaks for, or {@code null} for the whole series
 * @param itemId       the named piece, or {@code null}
 * @param artId        the kind of thing counted, or {@code null}
 * @param inventoryId  the inventory counted out of, or {@code null}
 * @param quantity     how many pieces the line asks for, always 1 on a named line
 * @param leadMinutes  how long before the appointment the gear is already gone
 * @param trailMinutes how long after the appointment the gear is still away
 * @param position     the display order within the appointment
 */
public record EquipmentNeed(
        int id,
        int eventId,
        LocalDate eventDate,
        Integer itemId,
        Integer artId,
        Integer inventoryId,
        int quantity,
        int leadMinutes,
        int trailMinutes,
        int position) {

    /** A day either way, which is the ordinary case and therefore the default. */
    public static final int DEFAULT_LEAD_MINUTES = 24 * 60;

    /**
     * What this line points at.
     *
     * @return the target
     */
    public LineTarget target() {
        return LineTarget.of(itemId, artId, inventoryId);
    }

    /**
     * Whether this line holds for every evening the series produces.
     *
     * @return {@code true} when the line carries no date of its own
     */
    public boolean forWholeSeries() {
        return eventDate == null;
    }

    /**
     * How long before the appointment the gear leaves the shelf.
     *
     * @return the lead
     */
    public Duration lead() {
        return Duration.ofMinutes(leadMinutes);
    }

    /**
     * How long after the appointment the gear is still away.
     *
     * @return the trail
     */
    public Duration trail() {
        return Duration.ofMinutes(trailMinutes);
    }

    /**
     * Creates a row mapping for database result set conversion.
     *
     * @return the mapping
     */
    public static RowMapping<EquipmentNeed> map() {
        return row -> new EquipmentNeed(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getObject("event_date", LocalDate.class),
                row.getObject("item_id", Integer.class),
                row.getObject("art_id", Integer.class),
                row.getObject("inventory_id", Integer.class),
                row.getInt("quantity"),
                row.getInt("lead_minutes"),
                row.getInt("trail_minutes"),
                row.getInt("position"));
    }
}
