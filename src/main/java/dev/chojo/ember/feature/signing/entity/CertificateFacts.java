/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * A certificate found in a checked document, named the way a reader compares it with a certificate
 * viewer or with what the installation publishes.
 *
 * @param subject           the certificate's subject, RFC 2253
 * @param serialNumber      its serial number, lower-case hexadecimal
 * @param sha256Fingerprint SHA-256 of the DER encoded certificate, upper-case hexadecimal in pairs
 *                          separated by colons
 */
public record CertificateFacts(String subject, String serialNumber, String sha256Fingerprint) {}
