/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * A partner's signing authority, pinned to the partnership.
 *
 * @param partnerId          the partnership it is pinned to
 * @param partner            the partner station and the name the partnership knows it by
 * @param sha256             SHA-256 of the certificate, lower-case hexadecimal
 * @param certificate        the authority's certificate, DER encoded
 * @param active             whether the partner last stated it as issuing new station certificates
 * @param kind               how it came to be pinned
 * @param pinnedAt           when it was pinned
 * @param lastListedAt       when a statement of the partner last named it
 * @param revocationList     its newest revocation list taken in, DER encoded; null while none came
 * @param revocationNextUpdate when that list says the next one is due; null while none came
 */
public record PinnedAuthority(
        int partnerId,
        SealingPartner partner,
        String sha256,
        byte[] certificate,
        boolean active,
        PinKind kind,
        Instant pinnedAt,
        Instant lastListedAt,
        byte @Nullable [] revocationList,
        @Nullable Instant revocationNextUpdate) {

    /** Maps a pin joined with its partnership's partner station and name. */
    public static RowMapping<PinnedAuthority> map() {
        return row -> new PinnedAuthority(
                row.getInt("partner_id"),
                new SealingPartner(row.get("partner_station_id", UUID_STRING), row.getString("partner_station_name")),
                row.getString("sha256"),
                row.getBytes("certificate"),
                row.getBoolean("active"),
                row.getEnum("pin_kind", PinKind.class),
                row.get("pinned_at", INSTANT_TIMESTAMP),
                row.get("last_listed_at", INSTANT_TIMESTAMP),
                row.getBytes("crl"),
                row.get("crl_next_update", INSTANT_TIMESTAMP));
    }

    /**
     * Whether the revocation list is missing or overdue, so a check would rest on stale revocation data.
     *
     * @param now the moment of the check
     * @return true when a fresh list should be asked for
     */
    public boolean revocationListDue(Instant now) {
        return revocationNextUpdate == null || !now.isBefore(revocationNextUpdate);
    }
}
