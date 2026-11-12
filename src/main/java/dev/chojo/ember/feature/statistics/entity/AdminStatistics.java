/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.chojo.ember.feature.mail.entity.EmailQueueStatus;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.FederationFigures;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.GrowthSeries;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.PeopleFigures;
import dev.chojo.ember.feature.statistics.entity.InstanceSeries.UsageSeries;
import dev.chojo.ember.feature.statistics.entity.StationStatistics.AttendanceMonth;

import java.util.List;

/**
 * The figures an instance administrator is shown about the whole instance.
 *
 * @param counts               every single figure, flattened into this object as the page reads it
 * @param window               how far back the series reach
 * @param emailByDay           mails sent per day, the last thirty days that sent any, newest first
 * @param emailByStatus        queued mails per status
 * @param signUpsByDay         sign-ups for appointments per day over the last thirty days
 * @param topStationsByMembers the ten stations with the most current members
 * @param activeByDay          people using the instance per day
 * @param growth               the size of the instance from the daily snapshot
 * @param attendanceByMonth    attendance entries per month in the window, by outcome
 * @param federation           partnerships, shares, lending, discovery and associations
 * @param peopleByType         the people of the instance by member type, now and per day
 */
public record AdminStatistics(
        @JsonUnwrapped AdminCounts counts,
        StatisticsWindow window,
        List<DayCount> emailByDay,
        List<EmailStatusCount> emailByStatus,
        List<DayCount> signUpsByDay,
        List<StationMembers> topStationsByMembers,
        UsageSeries activeByDay,
        GrowthSeries growth,
        List<AttendanceMonth> attendanceByMonth,
        FederationFigures federation,
        PeopleFigures peopleByType) {

    /**
     * Puts the totals of the instance and its series over a window together.
     *
     * @param totals the totals as they stand now
     * @param window how far back the series reach
     * @param series the series over that window
     * @return the figures of the page
     */
    public static AdminStatistics of(InstanceTotals totals, StatisticsWindow window, InstanceSeries series) {
        return new AdminStatistics(
                totals.counts(),
                window,
                totals.emailByDay(),
                totals.emailByStatus(),
                totals.signUpsByDay(),
                totals.topStationsByMembers(),
                series.activeByDay(),
                series.growth(),
                series.attendanceByMonth(),
                series.federation(),
                series.peopleByType());
    }

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
     * A station and how many current members it has.
     *
     * @param name        the station's name
     * @param memberCount its members who have not left
     */
    public record StationMembers(
            String name, @JsonProperty("member_count") int memberCount) {}
}
