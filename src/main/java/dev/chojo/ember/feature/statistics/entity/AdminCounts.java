/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.entity;

/**
 * Every single figure on the instance statistics page, read in one pass over the database.
 *
 * @param emailPending            mails waiting to be sent
 * @param emailSending            mails being handed to a provider right now
 * @param emailSentToday          mails sent today
 * @param emailFailed             mails given up on
 * @param emailSent               mails taken by a provider
 * @param mailProviderBlocks      addresses a provider refuses to deliver to
 * @param totalAccounts           every account
 * @param totalStations           every station somebody works in, cluster home stations left out
 * @param totalClusters           every cluster
 * @param totalMembers            every membership, former ones included
 * @param activeSessions          sign-ins used within the last seven days
 * @param pendingApplications     applications for a new station waiting for a decision
 * @param sessionsThisMonth       attendance sessions starting this month
 * @param totalInventoryItems     every inventory item
 * @param totalEvents             every appointment
 * @param totalAttendanceSessions every attendance session
 * @param totalAttendanceEntries  every attendance entry
 * @param totalProfileFields      every profile field
 * @param totalGroups             every member group
 * @param accountsVerified        accounts whose address is confirmed
 * @param accountsUnverified      accounts whose address is not confirmed yet
 * @param stationsSetupComplete   stations that finished their setup
 * @param stationsSetupPending    stations still in their setup
 * @param accountsWith2fa         accounts with at least one active second factor
 * @param eventsUpcoming          appointments that have not started yet
 * @param totalEventRegistrations every registration for an appointment
 */
public record AdminCounts(
        int emailPending,
        int emailSending,
        int emailSentToday,
        int emailFailed,
        int emailSent,
        int mailProviderBlocks,
        int totalAccounts,
        int totalStations,
        int totalClusters,
        int totalMembers,
        int activeSessions,
        int pendingApplications,
        int sessionsThisMonth,
        int totalInventoryItems,
        int totalEvents,
        int totalAttendanceSessions,
        int totalAttendanceEntries,
        int totalProfileFields,
        int totalGroups,
        int accountsVerified,
        int accountsUnverified,
        int stationsSetupComplete,
        int stationsSetupPending,
        int accountsWith2fa,
        int eventsUpcoming,
        int totalEventRegistrations) {}
