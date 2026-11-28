/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * What a station's cart asks of every lending station alike: the days, the occasion and a note.
 *
 * @param stationId the borrowing station
 * @param dateFrom  the first day wanted, or {@code null} while none is chosen
 * @param dateTo    the last day wanted, or {@code null} for a single day or none
 * @param occasion  what the gear is wanted for, empty when not written
 * @param note      what every lending station reads first in its request's messages, empty for none
 */
public record LendingCart(
        int stationId,
        @Nullable LocalDate dateFrom,
        @Nullable LocalDate dateTo,
        String occasion,
        String note) {

    /** A station's cart before anything was put in it. */
    public static LendingCart empty(int stationId) {
        return new LendingCart(stationId, null, null, "", "");
    }

    public static RowMapping<LendingCart> map() {
        return row -> new LendingCart(
                row.getInt("station_id"),
                row.getObject("date_from", LocalDate.class),
                row.getObject("date_to", LocalDate.class),
                row.getString("occasion"),
                row.getString("note"));
    }
}
