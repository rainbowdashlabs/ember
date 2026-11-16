/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {EventType} from '@/api/generated/schema'
import {copyStartDay, deadlineHoursBefore, lengthOf, nextSeriesDay, placedOn} from './copyDates'

/** A Wednesday. */
const TODAY = '2026-11-18'

describe('nextSeriesDay', () => {
    it('takes the next day of the weekday for a weekly series, today counting', () => {
        expect(nextSeriesDay(EventType.RECURRING, 1, '2025-01-06', TODAY)).toBe('2026-11-23')
        expect(nextSeriesDay(EventType.RECURRING, 3, '2025-01-08', TODAY)).toBe(TODAY)
    })

    it('takes the weekday of the first day where the series names none', () => {
        expect(nextSeriesDay(EventType.RECURRING, null, '2025-01-10', TODAY)).toBe('2026-11-20')
    })

    it('takes the first of the weekday in this month or, once passed, in the next', () => {
        expect(nextSeriesDay(EventType.MONTHLY_FIRST, 1, '2025-01-06', TODAY)).toBe('2026-12-07')
        expect(nextSeriesDay(EventType.MONTHLY_FIRST, 1, '2025-01-06', '2026-12-01')).toBe('2026-12-07')
    })

    it('keeps a quarterly series in the months it started in', () => {
        expect(nextSeriesDay(EventType.QUARTERLY, 5, '2025-01-03', '2026-11-02')).toBe('2027-01-01')
        expect(nextSeriesDay(EventType.QUARTERLY, 5, '2025-01-03', '2026-10-01')).toBe('2026-10-02')
        expect(nextSeriesDay(EventType.QUARTERLY, 5, '2025-01-03', '2026-10-03')).toBe('2027-01-01')
    })

    it('keeps the day and month of a yearly series and skips a year without a 29 February', () => {
        expect(nextSeriesDay(EventType.YEARLY, null, '2024-05-01', TODAY)).toBe('2027-05-01')
        expect(nextSeriesDay(EventType.YEARLY, null, '2024-12-24', TODAY)).toBe('2026-12-24')
        expect(nextSeriesDay(EventType.YEARLY, null, '2024-02-29', TODAY)).toBe('2028-02-29')
    })
})

describe('copyStartDay', () => {
    it('takes the next date the server names for a series still running', () => {
        expect(copyStartDay(EventType.QUARTERLY, 5, '2025-01-03', TODAY, '2027-01-01')).toBe('2027-01-01')
    })

    it('follows the pattern from today where the series has ended and the server names its last date', () => {
        expect(copyStartDay(EventType.RECURRING, 5, '2025-01-03', TODAY, '2025-06-27')).toBe('2026-11-20')
        expect(copyStartDay(EventType.RECURRING, 5, '2025-01-03', TODAY, null)).toBe('2026-11-20')
    })
})

describe('placedOn', () => {
    it('keeps the times of day and the days between start and end', () => {
        expect(placedOn('2025-01-03T19:00', '2025-01-05T14:30', '2026-11-20'))
            .toEqual({startTime: '2026-11-20T19:00', endTime: '2026-11-22T14:30'})
    })
})

describe('deadlineHoursBefore', () => {
    it('counts the hours the deadline lay before the start', () => {
        expect(deadlineHoursBefore('2026-05-12T18:00:00Z', '2026-05-09T12:00:00Z')).toBe(78)
    })

    it('has nothing without a deadline or with one after the start', () => {
        expect(deadlineHoursBefore('2026-05-12T18:00:00Z', null)).toBeNull()
        expect(deadlineHoursBefore('2026-05-12T18:00:00Z', '2026-05-13T18:00:00Z')).toBeNull()
    })
})

describe('lengthOf', () => {
    it('measures from the start to the end', () => {
        expect(lengthOf('2026-05-12T18:00', '2026-05-12T20:30')).toBe(150 * 60_000)
    })

    it('has nothing without both times', () => {
        expect(lengthOf('', '2026-05-12T20:30')).toBeNull()
    })
})
