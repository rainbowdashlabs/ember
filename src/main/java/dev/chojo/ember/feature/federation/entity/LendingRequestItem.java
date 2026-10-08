/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import org.jspecify.annotations.Nullable;

/**
 * One line of a lending request: a named piece, a count of one kind of thing, or a count out of a
 * whole inventory, all of them belonging to the station being asked.
 *
 * <p>Which pieces answer the line is not here. A line asking for four blue radios is answered with
 * four of them, and the assignment therefore lives in a table of its own rather than in a column that
 * could only ever hold the last one written.
 *
 * @param inventoryId the inventory the line draws from, or {@code null}
 * @param itemId      the piece the line names, or {@code null}
 * @param artId       the kind of thing the line asks for, or {@code null}
 * @param needId      the line of an appointment's needs this fills, or {@code null}
 * @param label       what the line asks for, as it was named when it was asked for, or as the
 *                    lending station named it where that station's gear is on another instance;
 *                    kept when the gear it names is gone
 */
public record LendingRequestItem(
        int id,
        int requestId,
        @Nullable Integer inventoryId,
        @Nullable Integer itemId,
        @Nullable Integer artId,
        int quantity,
        @Nullable Integer needId,
        String label) {

    /**
     * What this line points at, where it points at anything.
     *
     * @return the target, or {@code null} on a line that names nothing
     */
    public @Nullable LineTarget target() {
        if (itemId != null) return LineTarget.item(itemId);
        if (artId != null) return LineTarget.art(artId);
        if (inventoryId != null) return LineTarget.inventory(inventoryId);
        return null;
    }

    public static RowMapping<LendingRequestItem> map() {
        return row -> new LendingRequestItem(
                row.getInt("id"),
                row.getInt("request_id"),
                row.getObject("inventory_id", Integer.class),
                row.getObject("item_id", Integer.class),
                row.getObject("art_id", Integer.class),
                row.getInt("quantity"),
                row.getObject("need_id", Integer.class),
                row.getString("label"));
    }
}
