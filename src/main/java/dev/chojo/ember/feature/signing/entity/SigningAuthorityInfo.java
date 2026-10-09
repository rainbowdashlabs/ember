/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.time.Instant;

/**
 * One of the installation's signing authorities, as a reader of a sealed document checks it: the
 * fingerprint is what a reader pins, independently of the running installation.
 *
 * @param serialNumber      the authority certificate's serial number, lower-case hexadecimal, which
 *                          also names its certificate and revocation list addresses
 * @param subject           the certificate's subject, RFC 2253
 * @param sha256Fingerprint SHA-256 of the DER encoded certificate, upper-case hexadecimal in pairs
 *                          separated by colons
 * @param validFrom         when the certificate starts to hold
 * @param validUntil        when it expires
 * @param active            whether it issues new station certificates, false once it was retired
 */
public record SigningAuthorityInfo(
        String serialNumber,
        String subject,
        String sha256Fingerprint,
        Instant validFrom,
        Instant validUntil,
        boolean active) {}
