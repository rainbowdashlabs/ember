/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.entity;

import java.util.List;
import java.util.Map;

/**
 * The figures a station is shown about itself on its statistics page.
 *
 * <p>The two counts keyed by name are dictionaries on purpose: their keys are the station's own
 * group names and member kinds, which no record could spell out, and the page reads them as such.
 *
 * @param memberCount        every member of the station, former ones included
 * @param groupCounts        members per group, by group name in alphabetical order
 * @param attendanceByMonth  attendance of the last twelve months, oldest month first
 * @param inventoryStatus    items per inventory, by inventory name
 * @param eventRegistrations registrations of every upcoming appointment that has any
 * @param userTypeCounts     current members per kind of member, most common first
 */
public record StationStatistics(
        int memberCount,
        Map<String, Integer> groupCounts,
        List<AttendanceMonth> attendanceByMonth,
        List<InventoryStatus> inventoryStatus,
        List<EventRegistrations> eventRegistrations,
        Map<String, Integer> userTypeCounts) {

    /**
     * Attendance over one month.
     *
     * @param month    the month, as {@code YYYY-MM} in UTC
     * @param sessions sessions held that month
     * @param present  entries marked present
     * @param absent   entries marked absent
     * @param declined entries declined in advance
     */
    public record AttendanceMonth(String month, int sessions, int present, int absent, int declined) {}

    /**
     * The items of one inventory.
     *
     * @param name     the inventory's name
     * @param total    every item in it
     * @param assigned items handed to somebody
     * @param lost     items reported lost
     */
    public record InventoryStatus(String name, int total, int assigned, int lost) {}

    /**
     * The registrations of one upcoming appointment.
     *
     * @param name     the appointment's name
     * @param accepted registrations accepted
     * @param pending  registrations waiting for a decision
     * @param declined registrations declined or withdrawn
     */
    public record EventRegistrations(String name, int accepted, int pending, int declined) {}
}
