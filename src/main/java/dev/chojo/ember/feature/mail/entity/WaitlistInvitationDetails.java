/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

/**
 * What an invitation mail says about the evening somebody is asked to come to.
 *
 * <p>Everything is already a piece of text: the mail is the last stop, and a date the reader has to
 * make sense of is a date somebody chose how to write. Any field may be blank, and a blank one is
 * simply left out of the mail rather than printed empty.
 *
 * @param appointmentName what the appointment is called
 * @param date            the one date they are asked for
 * @param time            when the appointment itself runs
 * @param arrivalTime     when they were asked to be there, usually earlier than everybody else
 * @param location        where it is, when the appointment or the station says so
 */
public record WaitlistInvitationDetails(
        String appointmentName, String date, String time, String arrivalTime, String location) {

    public static final WaitlistInvitationDetails NONE = new WaitlistInvitationDetails("", "", "", "", "");
}
