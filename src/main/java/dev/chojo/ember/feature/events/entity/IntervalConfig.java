/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.time.LocalDate;
import java.time.LocalTime;

public record IntervalConfig(
        String intervalType,
        int dayOfWeek,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime) {}
