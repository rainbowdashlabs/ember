/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * What a list of event groups counts for each one.
 *
 * @param partCount how many parts it has
 * @param holding   how many accepted sign-ups hold a place, those of partners' members included
 * @param queued    how many sign-ups wait on the waiting list, those of partners' members included
 */
public record EventGroupTally(int partCount, int holding, int queued) {
    /** The tally of an event group with no parts and no sign-ups. */
    public static final EventGroupTally NONE = new EventGroupTally(0, 0, 0);
}
