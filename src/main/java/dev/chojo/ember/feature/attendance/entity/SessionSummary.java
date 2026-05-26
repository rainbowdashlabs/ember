/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import java.time.Instant;

/**
 * Summary of an attendance session including status counts.
 *
 * @param id               session ID
 * @param templateId       template ID
 * @param startTime        session start time
 * @param endTime          session end time
 * @param createdAt        creation timestamp
 * @param eventId          optional linked event ID
 * @param title            session title
 * @param presentCount     number of present entries
 * @param absentCount      number of absent entries
 * @param declinedCount    number of declined entries
 * @param unconfirmedCount number of unconfirmed entries
 */
public record SessionSummary(
        int id,
        int templateId,
        Instant startTime,
        Instant endTime,
        Instant createdAt,
        Integer eventId,
        String title,
        int presentCount,
        int absentCount,
        int declinedCount,
        int unconfirmedCount) {}
