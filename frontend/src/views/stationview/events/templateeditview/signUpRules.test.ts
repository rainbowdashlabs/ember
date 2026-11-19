/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {DeadlineAnchor, DeadlineKind, QueueOrder, RestrictionMode, type EventTemplate} from '@/api/generated/schema'
import {noDeadline} from '../eventshared/registrationDeadline'
import {nonNegative, signUpRulesOf} from './signUpRules'

/** A template with no sign-up rule set, so a test names only the rules it is about. */
function template(overrides: Partial<EventTemplate> = {}): EventTemplate {
    return {
        attendanceTemplateId: null, categoryId: null, description: null, eventType: null, id: 4, name: 'Übungsdienst',
        registrationDeadline: noDeadline(), minRegistrations: null, thresholdDays: null, registrationLimit: null,
        queueOrder: null, requiresConfirmation: null, requiresRegistration: null, restrictionMode: null,
        stationId: '00000000-0000-0000-0000-000000000000', title: null, viewRestrictionMode: RestrictionMode.AND,
        ...overrides,
    }
}

describe('signUpRules', () => {
    it('takes a count as a whole number of zero or more', () => {
        expect(nonNegative(3)).toBe(3)
        expect(nonNegative(-2)).toBe(0)
        expect(nonNegative(2.7)).toBe(2)
        expect(nonNegative('')).toBeUndefined()
        expect(nonNegative(undefined)).toBeUndefined()
    })
})

/** A template hands a form only the rules it sets, so what the form already holds for the others stays. */
describe('signUpRulesOf', () => {
    it('leaves out every rule the template leaves open', () => {
        expect(signUpRulesOf(template())).toEqual({})
    })

    it('hands over the rules the template sets, the deadline unchanged', () => {
        const deadline = {kind: DeadlineKind.RELATIVE, at: null, offsetMinutes: 10080, anchor: DeadlineAnchor.MIDNIGHT}

        expect(signUpRulesOf(template({
            registrationLimit: 12, queueOrder: QueueOrder.FAIRNESS, minRegistrations: 3, thresholdDays: 2, registrationDeadline: deadline,
        }))).toEqual({registrationLimit: 12, queueOrder: QueueOrder.FAIRNESS, minRegistrations: 3, thresholdDays: 2, registrationDeadline: deadline})
    })
})
