/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.entity;

import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.DayCount;
import dev.chojo.ember.feature.statistics.entity.StationStatistics.AttendanceMonth;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How the instance is used and how it grows, over a window of time.
 *
 * @param activeByDay       people using the instance per day
 * @param growth            the size of the instance from the daily snapshot
 * @param attendanceByMonth attendance entries per month by the start of their sheet, and the sheets
 * @param federation        partnerships, shares, lending, discovery and associations
 * @param peopleByType      the people of the instance by member type, now and per day
 */
public record InstanceSeries(
        UsageSeries activeByDay,
        GrowthSeries growth,
        List<AttendanceMonth> attendanceByMonth,
        FederationFigures federation,
        PeopleFigures peopleByType) {

    /**
     * People using the instance per day.
     *
     * @param today          accounts that used the instance today so far
     * @param average30      the average per day over the last thirty days, or over the days recorded
     *                       when that is fewer
     * @param recordedSince  the first day recorded, as {@code YYYY-MM-DD}, or {@code null} before the
     *                       first
     * @param days           one point per day, or per week as the average of its days, from the start
     *                       of the window or of the recording, whichever is later
     */
    public record UsageSeries(
            int today, double average30, @Nullable String recordedSince, List<DayCount> days) {}

    /**
     * The size of the instance from the daily snapshot.
     *
     * @param recordedSince the first day with a snapshot, as {@code YYYY-MM-DD}, or {@code null} before
     *                      the first
     * @param days          one point per day, or per week from its last snapshot
     */
    public record GrowthSeries(@Nullable String recordedSince, List<GrowthDay> days) {}

    /**
     * The size of the instance on one day.
     *
     * @param day               the day, or the first day of the week, as {@code YYYY-MM-DD}
     * @param accounts          accounts
     * @param members           memberships that are not archived
     * @param stations          stations, association home stations left out
     * @param events            appointments
     * @param attendanceEntries entries on attendance sheets
     * @param inventoryItems    pieces of equipment
     * @param associations      associations
     */
    public record GrowthDay(
            String day,
            int accounts,
            int members,
            int stations,
            int events,
            int attendanceEntries,
            int inventoryItems,
            int associations) {}

    /**
     * The people of the instance by member type.
     *
     * @param current the counts as they stand now
     * @param byDay   the same counts from the daily snapshot
     */
    public record PeopleFigures(PeopleByType current, List<PeopleDay> byDay) {}

    /**
     * The people by member type on one day.
     *
     * @param day    the day, or the first day of the week, as {@code YYYY-MM-DD}
     * @param counts the counts of that day
     */
    public record PeopleDay(String day, PeopleByType counts) {}

    /**
     * Federation across the instance.
     *
     * @param partnersByStatus       partnerships per status, counted once per side
     * @param localPartners          active partnerships with a station on this instance
     * @param remotePartners         active partnerships with a station on another instance
     * @param stationsWithPartner    stations with at least one active partnership
     * @param partnerInstances       other instances with at least one active partnership
     * @param sharesByKind           what stations share with their partners, per kind
     * @param lendingByMonth         lending requests per month in the window
     * @param peersKnown             other instances discovery knows
     * @param peersReachable         of those, the ones reachable and not blocked
     * @param associations           associations
     * @param stationsInAssociations stations that belong to an association
     */
    public record FederationFigures(
            List<PartnerStatusCount> partnersByStatus,
            int localPartners,
            int remotePartners,
            int stationsWithPartner,
            int partnerInstances,
            List<ShareCount> sharesByKind,
            List<MonthCount> lendingByMonth,
            int peersKnown,
            int peersReachable,
            int associations,
            int stationsInAssociations) {}

    /**
     * Partnerships in one status.
     *
     * @param status the status
     * @param count  how many partnerships are in it
     */
    public record PartnerStatusCount(FederationStatus status, int count) {}

    /**
     * Shares of one kind.
     *
     * @param kind  what is shared
     * @param count how many shares of it exist
     */
    public record ShareCount(FederationShareKind kind, int count) {}

    /**
     * A count for one month.
     *
     * @param month the month, as {@code YYYY-MM}
     * @param count what was counted in it
     */
    public record MonthCount(String month, int count) {}

    /** What a station can share with a partner. */
    public enum FederationShareKind {
        KNOWLEDGE_BASE,
        BOARD,
        QUIZ,
        PROTOCOL,
        EVENT,
        INVENTORY
    }
}
