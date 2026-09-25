/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.time.LocalDate;

/**
 * An appointment as a list of appointments shows it, with the date it next falls on and the date it
 * last fell on.
 *
 * <p>This is the appointment itself and not one of its occurrences: a series that comes round every
 * week is one row here and hundreds on a list of occurrences. A series written two years ago is
 * placed where it next falls rather than where it was written.
 *
 * <p>Both dates are stated on every row, so that a reader of one row never has to know which list it
 * came from to know what its date means. Which of them a list is ordered by does depend on the list:
 * the appointments that still come round read by {@code nextDate} ascending, and the ones that no
 * longer do read by {@code previousDate} descending. An appointment belongs to the second list
 * exactly when it has no next date at all, so {@code nextDate} is null on every row of it.
 *
 * @param event        the appointment
 * @param nextDate     the first date from today on that it falls on, null where it has none left
 * @param previousDate the last date before today that it fell on, null where it has yet to run
 */
public record DatedEvent(EventSummary event, LocalDate nextDate, LocalDate previousDate) {}
