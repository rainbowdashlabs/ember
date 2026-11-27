/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An event group as lists show it.
 *
 * @param id           the event group
 * @param details      what a manager sets on it: name, description, the sign-up, its colour and minimum
 * @param cancelled    whether it was called off
 * @param cancelReason why, where it was
 * @param partCount    how many parts it has
 * @param holding      how many accepted sign-ups hold a place
 * @param queued       how many sign-ups wait on the waiting list
 * @param pending      how many sign-ups wait for a decision; a partner is never sent it, so it reads 0 there
 * @param firstStart   when its earliest part starts, none without parts or where a partner was not sent them
 * @param lastEnd      when its latest part ends, none without parts or where a partner was not sent them
 * @param closesAt     when sign-ups close as the deadline places it now, from the start of the first
 *                     part for a relative one; none for no deadline, no part yet, or a relative one a
 *                     partner was not sent the parts of
 */
public record EventGroupSummary(
        int id,
        EventGroupDetails details,
        boolean cancelled,
        @Nullable String cancelReason,
        int partCount,
        int holding,
        int queued,
        int pending,
        @Nullable Instant firstStart,
        @Nullable Instant lastEnd,
        @Nullable Instant closesAt) {
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
                tally.holding(),
                tally.queued(),
                tally.pending(),
                tally.firstStart(),
                tally.lastEnd(),
                eventGroup.closesAt());
    }
}
