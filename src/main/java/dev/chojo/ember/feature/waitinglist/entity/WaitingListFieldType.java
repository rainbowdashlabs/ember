/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import dev.chojo.ember.feature.question.FieldType;

/**
 * The question types of a waiting list as the station's screens spell them.
 *
 * <p>Only the spelling is this feature's own: what a type means is {@link FieldType}, which each of
 * these names through {@link #fieldType()}.
 */
public enum WaitingListFieldType {
    TEXT,
    NUMBER,
    DATE,
    BOOLEAN,
    ENUM,
    /**
     * A date field carrying the date of birth. A list may declare at most one, which is what lets
     * the list work out an age without being told which field holds it. It is stored and answered
     * exactly like a {@link #DATE} field, so an ordinary date field becomes one without losing the
     * answers already given.
     */
    BIRTH_DATE;

    /** The shared name this type is stored under, which is also what every check and value reads. */
    public FieldType fieldType() {
        return this == ENUM ? FieldType.CHOICE : FieldType.valueOf(name());
    }

    /**
     * The type a column holds under its shared name.
     *
     * @param stored the name as the column holds it
     */
    public static WaitingListFieldType stored(String stored) {
        return FieldType.featureType(WaitingListFieldType.class, stored, WaitingListFieldType::fieldType);
    }
}
