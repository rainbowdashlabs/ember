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
 * An authority's newest revocation list as it is stored, with the time it was issued, so its age is
 * known without reading the list.
 *
 * @param list     the list, DER encoded
 * @param issuedAt the {@code thisUpdate} the list carries
 */
public record StoredRevocationList(byte[] list, Instant issuedAt) {

    /** Maps the stored list of an authority's row. */
    public static RowMapping<StoredRevocationList> map() {
        return row -> new StoredRevocationList(row.getBytes("crl"), row.get("crl_issued_at", INSTANT_TIMESTAMP));
    }
}
