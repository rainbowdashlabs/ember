/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

public enum TokenType {
    VERIFY_EMAIL,
    SET_PASSWORD,
    RESET_PASSWORD,
    FORCE_PASSWORD_CHANGE;

    public static TokenType fromValue(String value) {
        return valueOf(value.toUpperCase());
    }

    public String value() {
        return name().toLowerCase();
    }
}
