/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.wrapper.Row;
import dev.chojo.ember.util.Json;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The invitation an entry currently holds: the dates it offers and the station's personal note, or
 * {@code null} while nobody has been invited.
 *
 * <p>An appointment id alone does not name an occurrence: everything occurrence-shaped here is keyed
 * by appointment and date, and a weekly Dienst without a date would mean every Tuesday there has
 * ever been. So every offered date carries its appointment, and the person picks one of them by its
 * position.
 *
 * <p>Nobody is signed up from this. The person has not joined anything, so they are on no attendee
 * list and count towards no total the station plans from.
 *
 * <p>An option whose appointment has since been deleted is gone from the list, so an invitation can
 * be left with fewer dates than it was sent with, or with none and only its note.
 *
 * @param message a personal note the station wrote into the invitation, empty when it wrote none
 * @param options the dates offered, in the order the station put them, each at its position
 */
public record WaitingListInvitation(String message, List<OfferedDate> options) {

    /** The longest personal note an invitation carries. */
    public static final int MESSAGE_MAX_LENGTH = 2000;

    /** The most dates one invitation offers to pick from. */
    public static final int MAX_OPTIONS = 5;

    public WaitingListInvitation {
        options = List.copyOf(options);
    }

    /** An invitation to one date, without a personal note. */
    public WaitingListInvitation(int eventId, LocalDate date, @Nullable LocalTime arrivalTime) {
        this(eventId, date, arrivalTime, "");
    }

    /** An invitation to one date. */
    public WaitingListInvitation(int eventId, LocalDate date, @Nullable LocalTime arrivalTime, String message) {
        this(message, List.of(new OfferedDate(0, eventId, date, arrivalTime)));
    }

    /** The option at a position, or empty when the invitation offers none there. */
    public Optional<OfferedDate> option(int position) {
        return options.stream().filter(option -> option.position() == position).findFirst();
    }

    /**
     * One date an invitation offers.
     *
     * @param position    where it stands among the offered dates, which is how an answer names it
     * @param eventId     the appointment
     * @param date        the one date of it
     * @param arrivalTime     when they were asked to be there, usually earlier than everybody else, or
     *                        {@code null} when the station named no time of its own
     * @param appointmentName what the appointment is called, as it is read back for the station; empty
     *                        on a date that is being offered rather than read, since nothing reads it there
     */
    public record OfferedDate(
            int position,
            int eventId,
            LocalDate date,
            @Nullable LocalTime arrivalTime,
            String appointmentName) {

        /** A date as it is offered, before anything has read the appointment's name for it. */
        public OfferedDate(int position, int eventId, LocalDate date, @Nullable LocalTime arrivalTime) {
            this(position, eventId, date, arrivalTime, "");
        }
    }

    /**
     * Reads the invitation off an entry row, or {@code null} when the row carries neither a date nor
     * a note.
     */
    public static @Nullable WaitingListInvitation from(Row row) throws SQLException {
        String message = row.getString("invited_message");
        var options = readOptions(row.getString("invitation_options"));
        if (options.isEmpty() && (message == null || message.isBlank())) return null;
        return new WaitingListInvitation(message == null ? "" : message, options);
    }

    private static List<OfferedDate> readOptions(@Nullable String json) {
        if (json == null || json.isBlank()) return List.of();
        var options = new ArrayList<OfferedDate>();
        for (JsonNode node : Json.MAPPER.readTree(json)) {
            var arrival = node.get("arrival_time");
            options.add(new OfferedDate(
                    node.get("position").asInt(),
                    node.get("event_id").asInt(),
                    LocalDate.parse(node.get("event_date").asString()),
                    arrival == null || arrival.isNull() ? null : LocalTime.parse(arrival.asString()),
                    node.path("event_name").asString("")));
        }
        return options;
    }
}
