/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

/**
 * A set as the event group editor sends it.
 *
 * @param id      the set it changes, or null for a new one
 * @param name    its name
 * @param minimum how many of its parts a sign-up has to choose
 * @param maximum how many of its parts a sign-up may choose at most, or null for no maximum
 */
public record EventGroupSetDraft(
        @Nullable Integer id,
        String name,
        int minimum,
        @Nullable Integer maximum) {
    /**
     * A set without a maximum.
     *
     * @param id      the set to change, or null for a new one
     * @param name    its name
     * @param minimum how many of its parts a sign-up has to choose
     */
    public EventGroupSetDraft(@Nullable Integer id, String name, int minimum) {
        this(id, name, minimum, null);
    }
}
