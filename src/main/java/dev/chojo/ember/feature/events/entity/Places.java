/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

/**
 * The places of an appointment date or an event group. Only accepted sign-ups take a place; pending
 * ones wait for a decision and queued ones wait on the waiting list.
 *
 * @param limit    how many places there are, or null for no limit
 * @param accepted how many accepted sign-ups hold a place, which a manager may have taken past the limit
 * @param queued   how many sign-ups wait on the waiting list
 */
public record Places(@Nullable Integer limit, int accepted, int queued) {
    /** No limit, nobody accepted, nobody waiting. */
    public static final Places UNLIMITED = new Places(null, 0, 0);

    /**
     * Whether no place is free.
     *
     * @return true where a limit exists and the accepted sign-ups reach it
     */
    public boolean full() {
        return limit != null && accepted >= limit;
    }

    /**
     * How many places are free.
     *
     * @return the free places, never below zero, or null for no limit
     */
    public @Nullable Integer free() {
        return limit == null ? null : Math.max(0, limit - accepted);
    }
}
