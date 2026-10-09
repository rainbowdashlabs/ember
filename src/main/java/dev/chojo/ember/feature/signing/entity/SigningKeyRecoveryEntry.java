/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One recovery of the signing keys: which keys an instance administrator gave up, when and who.
 *
 * @param id                the recovery
 * @param recoveredAt       when the keys were given up
 * @param recoveredBy       the administrator's name; null once their account was deleted
 * @param authoritySerials  serial numbers of the authorities given up
 * @param stationKeySerials serial numbers of the station keys given up
 */
public record SigningKeyRecoveryEntry(
        int id,
        Instant recoveredAt,
        @Nullable String recoveredBy,
        List<String> authoritySerials,
        List<String> stationKeySerials) {

    /** Maps a recovery row joined with its administrator's name as {@code recovered_by}. */
    public static RowMapping<SigningKeyRecoveryEntry> map() {
        return row -> new SigningKeyRecoveryEntry(
                row.getInt("id"),
                row.get("recovered_at", INSTANT_TIMESTAMP),
                row.getString("recovered_by"),
                textArray(row.getArray("authority_serials")),
                textArray(row.getArray("station_key_serials")));
    }

    private static List<String> textArray(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }
}
