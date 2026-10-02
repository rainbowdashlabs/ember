/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry.AttendanceStatus;
import dev.chojo.ember.feature.mail.entity.EmailQueueStatus;

import java.util.List;

/**
 * The figures an instance administrator is shown about the whole instance.
 *
 * @param counts               every single figure, flattened into this object as the page reads it
 * @param emailByDay           mails sent per day, the last thirty days that sent any, newest first
 * @param emailByStatus        queued mails per status
 * @param attendanceByStatus   every attendance entry ever recorded, per outcome
 * @param registrationsByDay   registrations created per day over the last thirty days
 * @param sessionsByDay        sign-ins started per day over the last thirty days
 * @param topStationsByMembers the ten stations with the most current members
 */
public record AdminStatistics(
        @JsonUnwrapped AdminCounts counts,
        List<DayCount> emailByDay,
        List<EmailStatusCount> emailByStatus,
        List<AttendanceStatusCount> attendanceByStatus,
        List<DayCount> registrationsByDay,
        List<DayCount> sessionsByDay,
        List<StationMembers> topStationsByMembers) {

    /**
     * A count for one day.
     *
     * @param day   the day, as {@code YYYY-MM-DD}
     * @param count what was counted on it
     */
    public record DayCount(String day, int count) {}

    /**
     * Queued mails in one status.
     *
     * @param status the status
     * @param cnt    how many mails are in it
     */
    public record EmailStatusCount(EmailQueueStatus status, int cnt) {}

    /**
     * Attendance entries with one outcome.
     *
     * @param status the outcome
     * @param cnt    how many entries have it
     */
    public record AttendanceStatusCount(AttendanceStatus status, int cnt) {}

    /**
     * A station and how many current members it has.
     *
     * @param name        the station's name
     * @param memberCount its members who have not left
     */
    public record StationMembers(
            String name, @JsonProperty("member_count") int memberCount) {}
}
