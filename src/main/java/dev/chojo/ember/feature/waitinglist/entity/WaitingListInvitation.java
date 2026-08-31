/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The one appointment an entry has been invited to, or {@code null} while nobody has been invited.
 *
 * <p>An appointment id alone does not name an evening: everything occurrence-shaped here is keyed
 * by appointment and date, and a weekly Dienst without a date would mean every Tuesday there has
 * ever been. So the date travels with the appointment, and an answer given to the invitation
 * answers that pair.
 *
 * <p>Nobody is signed up from this. The person has not joined anything, so they are on no attendee
 * list and count towards no total the station plans from.
 *
 * @param eventId     the appointment they were asked to come to
 * @param date        the one date of it
 * @param arrivalTime when they were asked to be there, usually earlier than everybody else, or
 *                    {@code null} when the invitation named no time of its own
 */
public record WaitingListInvitation(int eventId, LocalDate date, LocalTime arrivalTime) {

    /** Reads the invitation off an entry row, or {@code null} when the row carries none. */
    public static WaitingListInvitation from(Row row) throws SQLException {
        var eventId = row.getObject("invited_event_id", Integer.class);
        if (eventId == null) return null;
        return new WaitingListInvitation(
                eventId,
                row.getObject("invited_event_date", LocalDate.class),
                row.getObject("invited_arrival_time", LocalTime.class));
    }
}
