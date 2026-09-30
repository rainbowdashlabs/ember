/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {OnboardingLevelName, OnboardingStatus} from '@/api/onboarding'

/**
 * What the task tour currently knows and is currently doing. Lives here rather than in one of the
 * composables because both the list and the guide on the page work with it, and a composable that
 * owned it would have to be imported by the other.
 *
 * <p>Held per request, like everything that belongs to a reader. Take it at the top of a setup or a
 * composable, before anything is awaited.
 */
export function onboardingState() {
    return {
        onboardingStatus: useState<Partial<Record<OnboardingLevelName, OnboardingStatus>>>(
            'onboardingState.status', () => ({})),
        /** The task being walked through right now, or null while the reader is left alone. */
        activeTaskId: useState<string | null>('onboardingState.activeTaskId', () => null),
        activeTaskKey: useState<string | null>('onboardingState.activeTaskKey', () => null),
        activeLevel: useState<OnboardingLevelName | null>('onboardingState.activeLevel', () => null),
        activeStep: useState('onboardingState.activeStep', () => 0),
        /** Set while the reader has waved the guide away for this visit. */
        guideDismissed: useState('onboardingState.guideDismissed', () => false),
        /**
         * Set the moment the introduction tour ends, so the task tour can take over without a reload
         * and without sending the reader back to the dashboard first.
         */
        handoverPending: useState('onboardingState.handoverPending', () => false),
    }
}

export type OnboardingState = ReturnType<typeof onboardingState>

/**
 * Leaves the reader alone: no task is walked through any more.
 *
 * @param state the tour's state, taken before whatever led here was awaited
 */
export function clearActiveTask(state: OnboardingState) {
    const {activeTaskId, activeTaskKey, activeLevel, activeStep} = state
    activeTaskId.value = null
    activeTaskKey.value = null
    activeLevel.value = null
    activeStep.value = 0
}
