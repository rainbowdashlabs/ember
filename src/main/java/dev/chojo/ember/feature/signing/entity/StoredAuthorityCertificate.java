/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * An authority's certificate as it is published, read without its private key.
 *
 * @param serialNumber the certificate's serial number, lower-case hexadecimal
 * @param certificate  the certificate, DER encoded
 * @param active       whether it issues new station certificates, false once it was retired
 */
public record StoredAuthorityCertificate(String serialNumber, byte[] certificate, boolean active) {

    /** Maps a row of the authorities that selects {@code active} beside the certificate. */
    public static RowMapping<StoredAuthorityCertificate> map() {
        return row -> new StoredAuthorityCertificate(
                row.getString("serial_number"), row.getBytes("certificate"), row.getBoolean("active"));
    }
}
