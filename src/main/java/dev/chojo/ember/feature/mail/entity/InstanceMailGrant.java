/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A station's leave to send its own mail through the instance's providers, after its own.
 *
 * @param stationId  the station
 * @param grantedAt  when an instance administrator granted it
 * @param dailyLimit how many mails a day the station may send through the instance's providers,
 *                   or null for no limit of its own
 */
public record InstanceMailGrant(
        int stationId, Instant grantedAt, @Nullable Integer dailyLimit) {

    /**
     * Whether the station's own limit leaves room after what it has already sent today.
     *
     * @param sentToday what the station sent through the instance's providers today
     */
    public boolean hasRoomToday(int sentToday) {
        return dailyLimit == null || sentToday < dailyLimit;
    }
}
