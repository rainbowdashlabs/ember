/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.entity;

import java.util.List;

/**
 * The tasks of one level and how they stand. Skipped tasks are counted apart from finished ones, so
 * a progress bar cannot claim work that nobody did.
 *
 * @param level   who these tasks belong to
 * @param tasks   every task that applies, in the order it is asked
 * @param open    how many are still to do
 * @param done    how many are finished
 * @param skipped how many were passed over
 */
public record OnboardingStatus(OnboardingLevel level, List<OnboardingTaskView> tasks, int open, int done, int skipped) {

    public static OnboardingStatus of(OnboardingLevel level, List<OnboardingTaskView> tasks) {
        int done = (int) tasks.stream()
                .filter(task -> task.state() == OnboardingTaskState.DONE)
                .count();
        int skipped = (int) tasks.stream()
                .filter(task -> task.state() == OnboardingTaskState.SKIPPED)
                .count();
        return new OnboardingStatus(level, tasks, tasks.size() - done - skipped, done, skipped);
    }
}
