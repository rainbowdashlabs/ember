/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A station's signing key as it is stored.
 *
 * @param id          the key's row id
 * @param authorityId the authority that issued its certificate
 * @param key         its certificate and wrapped private key
 */
public record StoredStationKey(int id, int authorityId, StoredSigningKey key) {

    /** Maps a row of the station keys. */
    public static RowMapping<StoredStationKey> map() {
        var key = StoredSigningKey.map();
        return row -> new StoredStationKey(row.getInt("id"), row.getInt("signing_ca_id"), key.map(row));
    }
}
