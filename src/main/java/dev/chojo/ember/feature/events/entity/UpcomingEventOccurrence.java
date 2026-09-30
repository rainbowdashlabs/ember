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
 *
 * <p>A date that was called off stays on the list and says so, rather than leaving a gap nobody can
 * explain.
 *
 * @param event        the appointment
 * @param date         the date it falls on
 * @param cancellation why that date is off, null while it takes place
 */
public record UpcomingEventOccurrence(EventSummary event, LocalDate date, CancellationNotice cancellation) {}
