/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

public enum TwoFactorKind {
    TOTP,
    WEBAUTHN,
    BACKUP_CODES
}
