/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A calendar subscription of a station for a shared calendar. It belongs to no member and shows every
 * appointment up to its level, except those of the categories it leaves out.
 *
 * @param id                 the feed
 * @param stationId          the station whose appointments it shows
 * @param name               the feed's name, also the calendar name
 * @param token              the token in the feed address
 * @param level              up to which visibility it shows appointments
 * @param createdBy          the member who created it, null once that member is gone
 * @param createdAt          when it was created
 * @param polledAt           when a calendar last fetched it, null while nothing has
 * @param hiddenCategoryIds  the categories it leaves out, in id order
 */
public record StationCalendarFeed(
        int id,
        int stationId,
        String name,
        String token,
        StationFeedLevel level,
        @Nullable Integer createdBy,
        Instant createdAt,
        @Nullable Instant polledAt,
        List<Integer> hiddenCategoryIds) {

    public static RowMapping<StationCalendarFeed> map() {
        return row -> new StationCalendarFeed(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("token"),
                row.getEnum("level", StationFeedLevel.class),
                row.getObject("created_by", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("polled_at", INSTANT_TIMESTAMP),
                ids(row.getArray("hidden_category_ids")));
    }

    private static List<Integer> ids(Array array) throws SQLException {
        return List.of((Integer[]) array.getArray());
    }
}
