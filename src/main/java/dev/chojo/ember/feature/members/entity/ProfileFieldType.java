/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * Supported data types for custom profile fields.
 */
public enum ProfileFieldType {
    TEXT,
    NUMBER,
    DATE,
    BOOLEAN,
    ENUM,
    AGE,
    /**
     * A date field carrying the member's date of birth. A station may declare at most one, which is
     * what lets anything needing a birthday find it without being told which field holds it.
     */
    BIRTH_DATE
}
