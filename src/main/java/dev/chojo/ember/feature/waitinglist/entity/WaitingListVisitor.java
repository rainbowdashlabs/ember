/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Somebody from a waiting list who said they come to one date of an appointment. They have joined
 * nothing, so they are on no attendee list; this is how whoever runs that evening learns they come.
 *
 * @param entryId     their waiting list entry
 * @param listId      the waiting list
 * @param listName    its name
 * @param firstname   their first name
 * @param lastname    their last name
 * @param date        the date they picked
 * @param arrivalTime when the station asked them to be there, or null where it named no time of its own
 */
public record WaitingListVisitor(
        int entryId,
        int listId,
        String listName,
        String firstname,
        String lastname,
        LocalDate date,
        @Nullable LocalTime arrivalTime) {}
