/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.entity;

/**
 * How far back the series of the instance statistics reach. The last thirty days are shown day by
 * day; the longer windows are shown week by week, so a chart keeps a readable number of points.
 */
public enum StatisticsWindow {
    /** The last thirty days, one point per day. */
    DAYS_30,
    /** The last twelve months, one point per week. */
    MONTHS_12,
    /** Everything recorded, one point per week. */
    ALL;

    /**
     * @return whether the series of this window are drawn one point per week rather than per day
     */
    public boolean byWeek() {
        return this != DAYS_30;
    }
}
