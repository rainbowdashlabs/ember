/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

import de.chojo.sadu.mapper.wrapper.Row;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * An RFC 3161 timestamp over the SHA-256 of a WebAuthn credential's public key, COSE encoded as it is
 * stored. It proves that the key existed at that time, so a key put in place later cannot pass for it.
 *
 * <p>The token is handed over as it is, without a copy.
 *
 * @param token     the timestamp token, a DER encoded CMS signed data whose message imprint is the
 *                  SHA-256 of the public key
 * @param stampedAt the time the token states
 * @param service   the address of the timestamp service that gave it
 * @param kind      how the stamp was obtained
 */
public record CredentialKeyStamp(byte[] token, Instant stampedAt, String service, KeyStampKind kind) {

    /**
     * Reads a stamp stored in the four columns {@code <prefix>key_stamp_token}, {@code <prefix>key_stamped_at},
     * {@code <prefix>key_stamp_service} and {@code <prefix>key_stamp_kind}.
     *
     * @param row    the row
     * @param prefix what the column names start with
     * @return the stamp, or null where the row holds none
     * @throws SQLException when a column cannot be read
     */
    public static @Nullable CredentialKeyStamp read(Row row, String prefix) throws SQLException {
        byte[] token = row.getBytes(prefix + "key_stamp_token");
        if (token == null) return null;
        return new CredentialKeyStamp(
                token,
                row.get(prefix + "key_stamped_at", INSTANT_TIMESTAMP),
                row.getString(prefix + "key_stamp_service"),
                row.getEnum(prefix + "key_stamp_kind", KeyStampKind.class));
    }
}
