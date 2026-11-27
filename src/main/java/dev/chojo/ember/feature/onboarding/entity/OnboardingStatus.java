/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.entity;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Counts what is left of a level's tasks.
     *
     * <p>A dismissed task is dropped here rather than reported: somebody threw it away, and a list
     * that keeps showing it, if only as a number, has not thrown anything away. Dropping it in the
     * one place every level passes through is also what lets the list run out entirely.
     */
    public static OnboardingStatus of(OnboardingLevel level, List<OnboardingTaskView> tasks) {
        List<OnboardingTaskView> listed = tasks.stream()
                .map(task -> task.withReady(ready(task, tasks)))
                .filter(task -> task.state() != OnboardingTaskState.DISMISSED)
                .toList();
        int done = (int) listed.stream()
                .filter(task -> task.state() == OnboardingTaskState.DONE)
                .count();
        int skipped = (int) listed.stream()
                .filter(task -> task.state() == OnboardingTaskState.SKIPPED)
                .count();
        return new OnboardingStatus(level, listed, listed.size() - done - skipped, done, skipped);
    }

    /**
     * Whether nothing this task waits for is still open.
     *
     * <p>Read against every task of the level, dismissed ones included, before those are dropped. A
     * prerequisite that is not in the list was not asked of this reader, and does not hold anything
     * back. A task about a member in somebody's care only waits for tasks about the same member.
     */
    private static boolean ready(OnboardingTaskView task, List<OnboardingTaskView> tasks) {
        Set<String> waitsFor =
                OnboardingTask.byKey(task.key()).map(OnboardingTask::prerequisites).orElse(Set.of()).stream()
                        .map(OnboardingTask::key)
                        .collect(Collectors.toSet());
        return tasks.stream()
                .filter(other -> waitsFor.contains(other.key()))
                .filter(other -> Objects.equals(other.subjectId(), task.subjectId()))
                .noneMatch(other -> other.state() == OnboardingTaskState.OPEN);
    }
}
