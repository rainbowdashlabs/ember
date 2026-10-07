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
 * A signing key as it is stored: its certificate and its private key, wrapped and never in clear.
 *
 * @param serialNumber      the certificate's serial number, lower-case hexadecimal
 * @param certificate       the certificate, DER encoded
 * @param wrappedPrivateKey the private key as the signing key wrap wrote it
 * @param validUntil        when the certificate expires
 */
public record StoredSigningKey(String serialNumber, byte[] certificate, byte[] wrappedPrivateKey, Instant validUntil) {

    /** Maps a row of the authority or of a station's keys. */
    public static RowMapping<StoredSigningKey> map() {
        return row -> new StoredSigningKey(
                row.getString("serial_number"),
                row.getBytes("certificate"),
                row.getBytes("wrapped_private_key"),
                row.get("valid_until", INSTANT_TIMESTAMP));
    }
}
