/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.entity;

/**
 * Who the switch to passwordless sign-in would leave behind.
 *
 * @param wouldKeepPassword accounts that keep their password when the mode switches
 * @param withoutPasskey of those, how many hold no passkey at all
 * @param reachableOnlyByQr of those, how many have neither a reachable address nor a
 *         guardian, which the QR code in the room is the only thing that gets to
 * @param dormantForAYear password holders who have not signed in for a year
 */
public record PasswordlessReport(
        int wouldKeepPassword, int withoutPasskey, int reachableOnlyByQr, int dormantForAYear) {}
