/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A signing key whose private key no longer opens under the at-rest secret, as an instance administrator
 * is shown it before giving it up.
 *
 * @param kind              an authority or a station key
 * @param serialNumber      its certificate's serial number, lower-case hexadecimal
 * @param sha256Fingerprint SHA-256 of its certificate, upper-case hexadecimal pairs separated by colons
 * @param active            whether it is the active authority or its station's active key
 * @param stationName       the station a station key belongs to; null for an authority
 * @param validUntil        when its certificate expires
 */
public record LockedSigningKey(
        SigningKeyKind kind,
        String serialNumber,
        String sha256Fingerprint,
        boolean active,
        @Nullable String stationName,
        Instant validUntil) {}
