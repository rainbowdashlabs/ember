/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Coverage status for the GDPR deletion ("right to be forgotten") workflow.
 *
 * @param status     current verification status
 * @param reason     required when {@code status = IGNORED}
 * @param strategies required when {@code status = TRACKED} - one entry per identity column
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GdprDeletionContext(
        TrackingStatus status,
        @Nullable String reason,
        @Nullable List<DeletionStrategy> strategies) {
    public GdprDeletionContext {
        strategies = strategies == null ? List.of() : strategies;
    }

    public static GdprDeletionContext unverified() {
        return new GdprDeletionContext(TrackingStatus.UNVERIFIED, null, List.of());
    }

    public static GdprDeletionContext ignored(String reason) {
        return new GdprDeletionContext(TrackingStatus.IGNORED, reason, List.of());
    }

    public static GdprDeletionContext tracked(List<DeletionStrategy> strategies) {
        return new GdprDeletionContext(TrackingStatus.TRACKED, null, strategies);
    }
}
