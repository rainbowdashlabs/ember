/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Router} from 'vue-router'

export const STEP_ORDER = [
    'welcome',
    'address',
    'modules',
    'member-types',
    'groups',
    'mail',
    'branding',
    'first-event',
    'kb-seed',
    'federation',
    'invites',
    'finish',
] as const

export type WizardStepId = (typeof STEP_ORDER)[number]

export function stepRouteName(stepId: WizardStepId): string {
    return `station-setup-${stepId}`
}

export function stepBackendId(stepId: WizardStepId): string | null {
    switch (stepId) {
        case 'welcome':
        case 'finish':
            return null
        case 'address':
            return 'address'
        case 'modules':
            return 'modules'
        case 'member-types':
            return 'memberTypes'
        case 'groups':
            return 'groups'
        case 'mail':
            return 'mail'
        case 'branding':
            return 'branding'
        case 'first-event':
            return 'firstEvent'
        case 'kb-seed':
            return 'kbSeed'
        case 'federation':
            return 'federation'
        case 'invites':
            return 'invites'
    }
}

export function nextStep(current: WizardStepId): WizardStepId | null {
    const idx = STEP_ORDER.indexOf(current)
    if (idx < 0) return null
    return STEP_ORDER[idx + 1] ?? null
}

export function prevStep(current: WizardStepId): WizardStepId | null {
    const idx = STEP_ORDER.indexOf(current)
    if (idx <= 0) return null
    return STEP_ORDER[idx - 1] ?? null
}

/**
 * Advances the wizard past {@code current}. The last step has nowhere to go, so it stays put and
 * the caller's own "finish" handling takes over.
 */
export function goToNextStep(router: Router, current: WizardStepId) {
    const next = nextStep(current)
    if (next) router.push({name: stepRouteName(next)})
}

/**
 * Where to come back to after a step hands off to a full page elsewhere in the app, such as
 * creating the first event or importing members. Falls back to the wizard index when the step is
 * the last one, so the user lands back in the wizard either way.
 */
export function nextStepHref(router: Router, current: WizardStepId): string {
    const next = nextStep(current)
    return next ? router.resolve({name: stepRouteName(next)}).href : '/station/setup'
}
