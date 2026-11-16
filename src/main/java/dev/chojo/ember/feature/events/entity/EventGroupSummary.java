/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

/**
 * An event group as lists show it.
 *
 * @param id           the event group
 * @param details      what a manager sets on it: name, description, the sign-up, its colour and minimum
 * @param cancelled    whether it was called off
 * @param cancelReason why, where it was
 * @param partCount    how many parts it has
 * @param holding      how many sign-ups hold or wait for a place
 */
public record EventGroupSummary(
        int id,
        EventGroupDetails details,
        boolean cancelled,
        @Nullable String cancelReason,
        int partCount,
        int holding) {
    /**
     * An event group with its counts as lists show it.
     *
     * @param eventGroup the event group
     * @param tally      its parts and holding sign-ups
     * @return the summary
     */
    public static EventGroupSummary of(EventGroup eventGroup, EventGroupTally tally) {
        return new EventGroupSummary(
                eventGroup.id(),
                eventGroup.details(),
                eventGroup.cancelled(),
                eventGroup.cancelReason(),
                tally.partCount(),
                tally.holding());
    }
}
