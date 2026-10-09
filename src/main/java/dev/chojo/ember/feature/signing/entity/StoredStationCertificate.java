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

/**
 * A station certificate as it is published, read without its private key.
 *
 * @param serialNumber          the certificate's serial number, lower-case hexadecimal
 * @param certificate           the certificate, DER encoded
 * @param authoritySerialNumber the serial number of the authority that issued it
 * @param revokedAt             when its key was revoked, or null while it is not
 */
public record StoredStationCertificate(
        String serialNumber,
        byte[] certificate,
        String authoritySerialNumber,
        @Nullable Instant revokedAt) {

    /** Maps a row of the station keys joined with the serial number of their authority. */
    public static RowMapping<StoredStationCertificate> map() {
        return row -> new StoredStationCertificate(
                row.getString("serial_number"),
                row.getBytes("certificate"),
                row.getString("authority_serial_number"),
                row.get("revoked_at", INSTANT_TIMESTAMP));
    }
}
