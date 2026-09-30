/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.entity;

import java.time.Instant;

/**
 * An account still holding a password beside a passkey it has tried.
 *
 * @param reachable whether mail to the member's own address can arrive
 * @param hasGuardian whether somebody manages the member and can hold up the QR code
 */
public record ResidueEntry(
        int accountId,
        String firstName,
        String lastName,
        Instant lastSignInAt,
        boolean reachable,
        boolean hasGuardian) {}
