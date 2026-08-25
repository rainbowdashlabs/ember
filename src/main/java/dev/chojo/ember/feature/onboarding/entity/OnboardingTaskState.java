/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.entity;

/**
 * Where an onboarding task stands. Skipped is kept apart from done because somebody who passes a
 * task over has decided something, and a count of what is set up must not claim more than that.
 */
public enum OnboardingTaskState {
    OPEN,
    DONE,
    SKIPPED
}
