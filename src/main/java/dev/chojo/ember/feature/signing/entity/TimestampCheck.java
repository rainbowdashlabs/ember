/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One timestamp found in a checked document, inside a signature or on its own.
 *
 * @param time            when the timestamp service says the stamped data existed; null when the
 *                        timestamp could not be read
 * @param authority       the timestamp service's certificate; null when the timestamp does not carry it
 * @param pinnedAuthority whether its chain ends in one of the timestamp service roots this installation
 *                        pins, the only ones it accepts timestamps from
 * @param indication      the validator's result for the timestamp
 * @param subIndication   why it did not pass; null when it passed
 * @param intact          whether the stamped data is unchanged and the timestamp's own signature holds
 */
public record TimestampCheck(
        @Nullable Instant time,
        @Nullable CertificateFacts authority,
        boolean pinnedAuthority,
        ValidationIndication indication,
        @Nullable ValidationSubIndication subIndication,
        boolean intact) {}
