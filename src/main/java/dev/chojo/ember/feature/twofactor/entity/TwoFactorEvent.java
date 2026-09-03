/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

public enum TwoFactorEvent {
    ENROLLED,
    REMOVED,
    LOGIN_VERIFIED,
    STEPUP_VERIFIED,
    BACKUP_CODE_USED,
    BACKUP_CODE_REGENERATED,
    ADMIN_RESET,
    TRUSTED_DEVICE_ADDED,
    TRUSTED_DEVICE_REVOKED,
    POLICY_CHANGED,
    PASSKEY_SIGN_IN,
    PASSKEY_ENROLLED_VIA_DEVICE_CODE,
    PASSKEY_CODE_ISSUED,
    PASSWORD_LOGIN_DISABLED,
    PASSWORD_LOGIN_ENABLED,
    PASSWORD_RETIRED,
    STEPUP_FAILED
}
