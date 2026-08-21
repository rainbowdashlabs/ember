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
    BIRTH_DATE,
    /**
     * A heading between fields rather than a field. It holds no answer, is never asked of anybody
     * and never leaves in an export: it exists so a long list of fields reads as the few groups of
     * things it actually is.
     */
    SECTION;

    /**
     * Whether this type holds an answer at all.
     */
    public boolean holdsValue() {
        return this != SECTION;
    }
}
