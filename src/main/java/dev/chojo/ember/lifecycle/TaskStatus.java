/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import java.time.Instant;

/**
 * What is known about one scheduled task since the instance started. Kept in memory only, so a
 * restart begins every task at {@link TaskOutcome#NOT_RUN_YET}.
 *
 * @param name               the task's name
 * @param mode               how its runs follow each other
 * @param periodSeconds      the distance between two runs, {@code 0} for a task that runs once
 * @param outcome            how the most recent run went, or that one is running now
 * @param lastStartedAt      when the most recent run started, {@code null} before the first
 * @param lastDurationMs     how long the most recent finished run took, {@code null} before the first
 * @param runs               how many runs finished since the start
 * @param failures           how many of them threw
 * @param lastFailureAt      when the most recent failed run started, {@code null} when none failed
 * @param lastFailureMessage what the most recent failed run threw, {@code null} when none failed
 */
public record TaskStatus(
        String name,
        Schedule.Mode mode,
        long periodSeconds,
        TaskOutcome outcome,
        Instant lastStartedAt,
        Long lastDurationMs,
        long runs,
        long failures,
        Instant lastFailureAt,
        String lastFailureMessage) {}
