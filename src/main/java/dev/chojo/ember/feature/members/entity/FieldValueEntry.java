/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

public record FieldValueEntry(int fieldId, String value, FieldOrigin origin) {

    /** An entry that names no origin is the station's own, which is what every caller but the profile is. */
    public FieldValueEntry(int fieldId, String value) {
        this(fieldId, value, FieldOrigin.STATION);
    }
}
