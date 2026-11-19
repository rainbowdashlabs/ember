/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {DeadlineKind, type EventTemplate} from '@/api/generated/schema'
import {deadlineRuleOf} from '../eventshared/registrationDeadline'

/** The sign-up rules a template hands an appointment form: only those the template sets. */
export type TemplateSignUpRules = Partial<Pick<EventTemplate,
    'registrationLimit' | 'queueOrder' | 'minRegistrations' | 'thresholdDays' | 'registrationDeadline'>>

/**
 * The sign-up rules a template sets, for a form to take over with `Object.assign`. A rule the template
 * leaves open is left out, so whatever the form already holds for it stays.
 *
 * @param template the template applied
 */
export function signUpRulesOf(template: EventTemplate): TemplateSignUpRules {
    const rules: TemplateSignUpRules = {}
    if (template.registrationLimit != null) rules.registrationLimit = template.registrationLimit
    if (template.queueOrder != null) rules.queueOrder = template.queueOrder
    if (template.minRegistrations != null) rules.minRegistrations = template.minRegistrations
    if (template.thresholdDays != null) rules.thresholdDays = template.thresholdDays
    const deadline = deadlineRuleOf(template.registrationDeadline)
    if (deadline.kind !== DeadlineKind.NONE) rules.registrationDeadline = deadline
    return rules
}

/**
 * A count typed into a number field, as a whole number of zero or more, or undefined for a field
 * left empty.
 *
 * <p>The server refuses a negative count, and a number field hands over an empty string when it is
 * cleared, so neither is passed on as it came.
 *
 * @param value what the field holds
 */
export function nonNegative(value: unknown): number | undefined {
    if (typeof value !== 'number' || Number.isNaN(value)) return undefined
    return Math.max(0, Math.trunc(value))
}
