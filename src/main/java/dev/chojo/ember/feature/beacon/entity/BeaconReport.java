/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A forwarded report, with whoever can be written to about it.
 *
 * @param screenshotFileId the picture that came with it, or null where the report arrived without one
 * @param instanceId       which installation sent it, worked out from the key that signed the delivery.
 *                         A beacon collects from many, and an instance that has named no contact would
 *                         otherwise be indistinguishable from every other instance that has named none:
 *                         three reports would read alike whether they came from one installation or
 *                         three. This is the one name a beacon always has for a sender.
 */
public record BeaconReport(
        int id,
        String message,
        @Nullable String page,
        @Nullable String version,
        @Nullable String browser,
        @Nullable String screenSize,
        @Nullable String roles,
        @Nullable String recentRequests,
        @Nullable String contactName,
        @Nullable String contactMail,
        Instant reportedAt,
        boolean acknowledged,
        @Nullable Integer screenshotFileId,
        String instanceId) {}
