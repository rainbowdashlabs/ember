/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.time.LocalDate;

/**
 * A single occurrence of an event on a specific date.
 * Used by the upcoming view to display a paginated, chronologically sorted list of event occurrences
 * (expanding recurring events into individual dates).
 */
public record UpcomingEventOccurrence(EventSummary event, LocalDate date) {}
