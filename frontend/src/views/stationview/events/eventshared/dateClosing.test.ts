/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {closesOnDate, hasPassed, occurrenceStartOn} from './dateClosing'
import type {RegistrationDeadline} from '@/api/generated/schema'
import {noDeadline} from './registrationDeadline'

const BERLIN = 'Europe/Berlin'
const WEEK_AT_MIDNIGHT: RegistrationDeadline = {kind: 'RELATIVE', at: null, offsetMinutes: 7 * 1440, anchor: 'MIDNIGHT'}

describe('occurrenceStartOn', () => {
    it('keeps the first start on its own day', () => {
        expect(occurrenceStartOn('2026-11-21T09:00:00Z', '2026-11-21', BERLIN).toISOString()).toBe('2026-11-21T09:00:00.000Z')
    })

    it('keeps the hour on the station clock across the change to summer time', () => {
        expect(occurrenceStartOn('2026-03-21T09:00:00Z', '2026-03-28', BERLIN).toISOString()).toBe('2026-03-28T09:00:00.000Z')
        expect(occurrenceStartOn('2026-03-21T09:00:00Z', '2026-04-04', BERLIN).toISOString()).toBe('2026-04-04T08:00:00.000Z')
    })
})

describe('closesOnDate', () => {
    it('closes each date of a series on its own with a relative rule', () => {
        expect(closesOnDate(WEEK_AT_MIDNIGHT, '2026-11-21T09:00:00Z', '2026-11-28', BERLIN)).toBe('2026-11-20T23:00:00.000Z')
    })

    it('closes every date at once with a fixed rule', () => {
        const fixed: RegistrationDeadline = {kind: 'FIXED', at: '2026-11-01T12:00:00Z', offsetMinutes: null, anchor: null}
        expect(closesOnDate(fixed, '2026-11-21T09:00:00Z', '2026-12-05', BERLIN)).toBe('2026-11-01T12:00:00.000Z')
    })

    it('says nothing where sign-up does not close', () => {
        expect(closesOnDate(noDeadline(), '2026-11-21T09:00:00Z', '2026-11-21', BERLIN)).toBeNull()
        expect(closesOnDate(null, '2026-11-21T09:00:00Z', '2026-11-21', BERLIN)).toBeNull()
    })
})

describe('hasPassed', () => {
    it('reads an instant before now as passed and nothing as open', () => {
        expect(hasPassed('2020-01-01T00:00:00Z')).toBe(true)
        expect(hasPassed('2999-01-01T00:00:00Z')).toBe(false)
        expect(hasPassed(null)).toBe(false)
    })
})
