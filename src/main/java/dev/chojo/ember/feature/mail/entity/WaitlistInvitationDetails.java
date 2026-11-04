/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import java.util.List;
import java.util.Map;

/**
 * What an invitation mail says about the dates somebody is asked to pick from.
 *
 * <p>Everything is already a piece of text: the mail is the last stop, and a date the reader has to
 * make sense of is a date somebody chose how to write. Any field may be blank, and a blank one is
 * simply left out of the mail rather than printed empty.
 *
 * @param appointments the dates offered, in the order the station put them; empty when every one of
 *                     them has since been deleted
 * @param message      the personal note the station wrote into the invitation
 */
public record WaitlistInvitationDetails(List<Appointment> appointments, String message) {

    public static final WaitlistInvitationDetails NONE = new WaitlistInvitationDetails(List.of(), "");

    public WaitlistInvitationDetails {
        appointments = List.copyOf(appointments);
    }

    /**
     * One offered date as the mail and the page write it.
     *
     * @param position        where it stands among the offered dates, which is how an answer names it
     * @param appointmentName what the appointment is called
     * @param date            the one date they are asked for
     * @param time            when the appointment itself runs
     * @param arrivalTime     when they were asked to be there, usually earlier than everybody else
     * @param location        where it is, when the appointment or the station says so
     */
    public record Appointment(
            int position, String appointmentName, String date, String time, String arrivalTime, String location) {

        /** The appointment as the mail template reads it. */
        public Map<String, String> templateVariables() {
            return Map.of(
                    "appointmentName", appointmentName,
                    "appointmentDate", date,
                    "appointmentTime", time,
                    "arrivalTime", arrivalTime,
                    "location", location);
        }

        /** The appointment named in one line, for a notification that has to say which date was picked. */
        public String summary() {
            return date.isBlank() ? appointmentName : appointmentName + ", " + date;
        }
    }
}
