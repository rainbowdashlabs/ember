/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {closesAt, hasPassed, noDeadline, sameRule} from './registrationDeadline'
import type {RegistrationDeadline} from '@/api/generated/schema'

const BERLIN = 'Europe/Berlin'

function relative(offsetMinutes: number, anchor: 'EXACT' | 'MIDNIGHT'): RegistrationDeadline {
    return {kind: 'RELATIVE', at: null, offsetMinutes, anchor}
}

/**
 * The deadline the editor previews has to be the one the server enforces, so the computation is held
 * to the same cases as the server's: both anchors, and midnight on either side of a change to or from
 * summer time in Berlin.
 */
describe('closesAt', () => {
    it('never closes without a deadline', () => {
        expect(closesAt(noDeadline(), '2026-11-21T09:00:00Z', BERLIN)).toBeNull()
    })

    it('closes every date at the same fixed moment', () => {
        const rule: RegistrationDeadline = {kind: 'FIXED', at: '2026-11-01T12:00:00Z', offsetMinutes: null, anchor: null}

        expect(closesAt(rule, '2026-11-21T09:00:00Z', BERLIN)?.toISOString()).toBe('2026-11-01T12:00:00.000Z')
        expect(closesAt({...rule, at: null}, '2026-11-21T09:00:00Z', BERLIN)).toBeNull()
    })

    it('closes seven days before a Saturday at ten at midnight of the Saturday a week earlier', () => {
        const start = new Date('2026-11-21T09:00:00Z')

        const closing = closesAt(relative(7 * 1440, 'MIDNIGHT'), start, BERLIN)

        expect(closing?.toISOString()).toBe('2026-11-13T23:00:00.000Z')
        expect((start.getTime() - (closing?.getTime() ?? 0)) / 3_600_000).toBe(178)
    })

    it('closes exactly the span before the start', () => {
        expect(closesAt(relative(90, 'EXACT'), '2026-11-21T09:00:00Z', BERLIN)?.toISOString())
            .toBe('2026-11-21T07:30:00.000Z')
    })

    it('finds midnight in winter time on the day summer time begins', () => {
        const start = '2026-03-30T08:00:00Z'

        expect(closesAt(relative(1440, 'MIDNIGHT'), start, BERLIN)?.toISOString()).toBe('2026-03-28T23:00:00.000Z')
        expect(closesAt(relative(1440, 'EXACT'), start, BERLIN)?.toISOString()).toBe('2026-03-29T08:00:00.000Z')
    })

    it('finds midnight in summer time on the day summer time ends', () => {
        expect(closesAt(relative(1440, 'MIDNIGHT'), '2026-10-26T09:00:00Z', BERLIN)?.toISOString())
            .toBe('2026-10-24T22:00:00.000Z')
    })

    it('finds midnight in summer time a week before a start in winter time', () => {
        expect(closesAt(relative(7 * 1440, 'MIDNIGHT'), '2026-10-31T09:00:00Z', BERLIN)?.toISOString())
            .toBe('2026-10-23T22:00:00.000Z')
    })

    it('counts whole days back on the calendar, not in hours, across the start of summer time', () => {
        expect(closesAt(relative(7 * 1440, 'MIDNIGHT'), '2026-04-03T22:30:00Z', BERLIN)?.toISOString())
            .toBe('2026-03-27T23:00:00.000Z')
    })

    it('takes a span of hours off the start before going down to midnight', () => {
        expect(closesAt(relative(25 * 60, 'MIDNIGHT'), '2026-11-21T00:30:00Z', BERLIN)?.toISOString())
            .toBe('2026-11-19T23:00:00.000Z')
    })

    it('counts midnight on the station clock, not in London', () => {
        expect(closesAt(relative(60, 'MIDNIGHT'), '2026-11-21T00:30:00Z', BERLIN)?.toISOString())
            .toBe('2026-11-20T23:00:00.000Z')
    })

    it('has no deadline for a start nobody can read or a span that is missing', () => {
        expect(closesAt(relative(60, 'EXACT'), 'not a date', BERLIN)).toBeNull()
        expect(closesAt({kind: 'RELATIVE', at: null, offsetMinutes: null, anchor: 'EXACT'}, '2026-11-21T09:00:00Z', BERLIN)).toBeNull()
    })
})

describe('sameRule', () => {
    it('compares what the rules say, not which objects hold them', () => {
        expect(sameRule(relative(1440, 'EXACT'), relative(1440, 'EXACT'))).toBe(true)
        expect(sameRule(relative(1440, 'EXACT'), relative(1440, 'MIDNIGHT'))).toBe(false)
        expect(sameRule(relative(60, 'EXACT'), null)).toBe(false)
        expect(sameRule(null, null)).toBe(true)
    })
})

describe('hasPassed', () => {
    it('reads an instant before now as passed and nothing as open', () => {
        expect(hasPassed('2020-01-01T00:00:00Z')).toBe(true)
        expect(hasPassed('2999-01-01T00:00:00Z')).toBe(false)
        expect(hasPassed(null)).toBe(false)
    })
})
