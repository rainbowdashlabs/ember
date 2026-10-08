/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * A stored signing key that was not given up and still holds its private key, read to check whether it
 * opens under the at-rest secret.
 *
 * @param kind        an authority or a station key
 * @param id          its row id in the table of its kind
 * @param authorityId the authority that issued a station key; null for an authority
 * @param stationName the name of a station key's station; null for an authority
 * @param active      whether it is the active authority or its station's active key
 * @param key         its certificate and wrapped private key
 */
public record KeyInUse(
        SigningKeyKind kind,
        int id,
        @Nullable Integer authorityId,
        @Nullable String stationName,
        boolean active,
        StoredSigningKey key) {

    /** Maps a row that selects the key columns, {@code id}, {@code active}, {@code signing_ca_id} and {@code station_name}. */
    public static RowMapping<KeyInUse> map(SigningKeyKind kind) {
        var key = StoredSigningKey.map();
        return row -> new KeyInUse(
                kind,
                row.getInt("id"),
                kind == SigningKeyKind.AUTHORITY ? null : row.getInt("signing_ca_id"),
                row.getString("station_name"),
                row.getBoolean("active"),
                key.map(row));
    }
}
