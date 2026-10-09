/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One of the installation's signing authorities as it is stored.
 *
 * @param id  the authority's row id
 * @param key its certificate and wrapped private key
 */
public record StoredAuthority(int id, StoredSigningKey key) {

    /** Maps a row of the authorities. */
    public static RowMapping<StoredAuthority> map() {
        var key = StoredSigningKey.map();
        return row -> new StoredAuthority(row.getInt("id"), key.map(row));
    }
}
