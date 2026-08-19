/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

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
    BIRTH_DATE
}
