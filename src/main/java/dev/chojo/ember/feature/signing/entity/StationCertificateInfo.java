/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One of a station's seal certificates, as a reader of a document it sealed checks it.
 *
 * @param serialNumber          the certificate's serial number, lower-case hexadecimal
 * @param subject               the certificate's subject, RFC 2253
 * @param sha256Fingerprint     SHA-256 of the DER encoded certificate, upper-case hexadecimal in
 *                              pairs separated by colons
 * @param validFrom             when the certificate starts to hold
 * @param validUntil            when it expires
 * @param authoritySerialNumber the serial number of the authority that issued it
 * @param revokedAt             when its key was revoked, or null while it is not
 */
public record StationCertificateInfo(
        String serialNumber,
        String subject,
        String sha256Fingerprint,
        Instant validFrom,
        Instant validUntil,
        String authoritySerialNumber,
        @Nullable Instant revokedAt) {}
