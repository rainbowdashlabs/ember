/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One passkey as the member's own list shows it. {@code lastUsedAt} doubles as the evidence a
 * passkey has been shown to work: the trial after creation and every real sign-in write it
 * through the same code path.
 *
 * @param secondFactor whether the member also opted this credential into the password path
 * @param credentialId the authenticator's credential id, which the browser needs to tell its
 *         own store which credentials still exist after a removal
 * @param userHandle the account's stable user handle, for the same signal
 */
public record PasskeyListEntry(
        int factorId,
        String label,
        Instant createdAt,
        Instant lastUsedAt,
        UUID aaguid,
        boolean secondFactor,
        byte[] credentialId,
        byte[] userHandle) {

    public static RowMapping<PasskeyListEntry> map() {
        return row -> new PasskeyListEntry(
                row.getInt("id"),
                row.getString("label"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("last_used_at", INSTANT_TIMESTAMP),
                row.get("aaguid", StandardValueConverter.UUID_STRING),
                row.getBoolean("second_factor"),
                row.getBytes("credential_id"),
                row.getBytes("user_handle"));
    }

    public boolean tried() {
        return lastUsedAt != null;
    }
}
