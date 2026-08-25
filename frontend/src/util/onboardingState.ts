/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import type {OnboardingLevelName, OnboardingStatus} from '@/api/onboarding'

/**
 * What the task tour currently knows and is currently doing. Lives here rather than in one of the
 * composables because both the list and the guide on the page work with it, and a composable that
 * owned it would have to be imported by the other.
 */
export const onboardingStatus = ref<Partial<Record<OnboardingLevelName, OnboardingStatus>>>({})

/** The task being walked through right now, or null while the reader is left alone. */
export const activeTaskId = ref<string | null>(null)
export const activeTaskKey = ref<string | null>(null)
export const activeLevel = ref<OnboardingLevelName | null>(null)
export const activeStep = ref(0)

/** Set while the reader has waved the guide away for this visit. */
export const guideDismissed = ref(false)

/**
 * Set the moment the introduction tour ends, so the task tour can take over without a reload and
 * without sending the reader back to the dashboard first.
 */
export const handoverPending = ref(false)

export function clearActiveTask() {
    activeTaskId.value = null
    activeTaskKey.value = null
    activeLevel.value = null
    activeStep.value = 0
}
