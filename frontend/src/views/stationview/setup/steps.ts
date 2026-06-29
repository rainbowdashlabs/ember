/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
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
    if (idx < 0 || idx === STEP_ORDER.length - 1) return null
    return STEP_ORDER[idx + 1]
}

export function prevStep(current: WizardStepId): WizardStepId | null {
    const idx = STEP_ORDER.indexOf(current)
    if (idx <= 0) return null
    return STEP_ORDER[idx - 1]
}
