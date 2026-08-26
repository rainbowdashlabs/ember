/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * What somebody said about one task: that they ticked it off, that they passed it over, or that they
 * threw it away.
 *
 * @param taskKey the task this is about
 * @param state   {@code CONFIRMED}, {@code SKIPPED} or {@code DISMISSED}
 * @param changedAt when it was said
 * @param actorId who said it, on the shared levels, or null on a member's own tasks and once that
 *                person is gone
 */
public record OnboardingMark(String taskKey, String state, Instant changedAt, Integer actorId) {
    public static RowMapping<OnboardingMark> map(String actorColumn) {
        return row -> new OnboardingMark(
                row.getString("task_key"),
                row.getString("state"),
                row.get("changed_at", INSTANT_TIMESTAMP),
                actorColumn == null ? null : row.getObject(actorColumn, Integer.class));
    }

    public boolean skipped() {
        return "SKIPPED".equals(state);
    }

    public boolean confirmed() {
        return "CONFIRMED".equals(state);
    }

    /** Whether the task was thrown away for good and is not to be listed again. */
    public boolean dismissed() {
        return "DISMISSED".equals(state);
    }
}
