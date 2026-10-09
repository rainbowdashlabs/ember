/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A revoked station key, as its authority's revocation list names it.
 *
 * @param serialNumber the key's certificate serial number, lower-case hexadecimal
 * @param revokedAt    when it was revoked
 * @param reason       why it was revoked
 */
public record RevokedKey(String serialNumber, Instant revokedAt, RevocationReason reason) {

    /** Maps a row of the station keys that was revoked. */
    public static RowMapping<RevokedKey> map() {
        return row -> new RevokedKey(
                row.getString("serial_number"),
                row.get("revoked_at", INSTANT_TIMESTAMP),
                row.getEnum("revocation_reason", RevocationReason.class));
    }
}
