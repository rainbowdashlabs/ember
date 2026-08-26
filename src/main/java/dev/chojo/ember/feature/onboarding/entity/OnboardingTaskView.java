/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.entity;

import java.time.Instant;

/**
 * One task as its reader sees it.
 *
 * @param id          what the reader sends back to tick it off or pass it over. Carries the member
 *                    a task is about where the same task is asked once per member in their care
 * @param key         the catalogue key, which is what the text is looked up under
 * @param subject     the first name of the member the task is about, or null when it is about
 *                    nobody in particular
 * @param subjectId   the member the task is about, or null
 * @param state       where the task stands
 * @param confirmable whether it can be ticked off by hand, which a derived task cannot
 * @param actorName   who ticked it off or passed it over, on the shared levels only
 * @param changedAt   when that was
 */
public record OnboardingTaskView(
        String id,
        String key,
        String subject,
        Integer subjectId,
        OnboardingTaskState state,
        boolean confirmable,
        String actorName,
        Instant changedAt) {}
