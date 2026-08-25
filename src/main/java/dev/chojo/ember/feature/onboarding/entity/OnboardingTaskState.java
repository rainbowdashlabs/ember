/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.entity;

/**
 * Where an onboarding task stands. Skipped is kept apart from done because somebody who passes a
 * task over has decided something, and a count of what is set up must not claim more than that.
 *
 * <p>Dismissed is the end of a skipped task rather than a state anybody looks at: it is thrown away
 * for good and never listed again, which is what a list of first steps needs so that it can stop
 * being a list at all.
 */
public enum OnboardingTaskState {
    OPEN,
    DONE,
    SKIPPED,
    DISMISSED
}
