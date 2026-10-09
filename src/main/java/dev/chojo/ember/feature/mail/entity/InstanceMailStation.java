/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * One station as the instance's administrators see it when they decide who may send through the
 * instance's mail providers.
 *
 * @param stationUid the station
 * @param name       its name
 * @param granted    whether it may send through the instance's providers
 * @param grantedAt  since when, or null while it may not
 * @param dailyLimit how many mails a day it may send through them, or null for no limit of its own
 * @param sentToday  how many of its mails the instance's providers sent today
 */
public record InstanceMailStation(
        UUID stationUid,
        String name,
        boolean granted,
        @Nullable Instant grantedAt,
        @Nullable Integer dailyLimit,
        int sentToday) {}
