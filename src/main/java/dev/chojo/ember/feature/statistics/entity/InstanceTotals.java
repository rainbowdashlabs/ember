/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.entity;

import dev.chojo.ember.feature.statistics.entity.AdminStatistics.DayCount;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.EmailStatusCount;
import dev.chojo.ember.feature.statistics.entity.AdminStatistics.StationMembers;

import java.util.List;

/**
 * The figures of the instance statistics page that do not depend on a window of time.
 *
 * @param counts               every single figure
 * @param emailByDay           mails sent per day, the last thirty days that sent any, newest first
 * @param emailByStatus        queued mails per status
 * @param signUpsByDay         sign-ups for appointments per day over the last thirty days
 * @param topStationsByMembers the ten stations with the most current members
 */
public record InstanceTotals(
        AdminCounts counts,
        List<DayCount> emailByDay,
        List<EmailStatusCount> emailByStatus,
        List<DayCount> signUpsByDay,
        List<StationMembers> topStationsByMembers) {}
