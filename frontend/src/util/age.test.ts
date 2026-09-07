/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {ageOn, computeAge, endOfYear} from './age'

describe('ageOn', () => {
    it('counts a birthday as reached on the day itself', () => {
        expect(ageOn('2016-09-07', new Date(2026, 8, 7))).toBe(10)
        expect(ageOn('2016-09-08', new Date(2026, 8, 7))).toBe(9)
    })

    it('handles a year boundary', () => {
        expect(ageOn('2016-12-31', new Date(2026, 11, 30))).toBe(9)
        expect(ageOn('2016-12-31', new Date(2026, 11, 31))).toBe(10)
    })

    it('returns null where nothing readable stands', () => {
        expect(ageOn('', new Date())).toBeNull()
        expect(ageOn('soon', new Date())).toBeNull()
    })
})

describe('endOfYear', () => {
    it('is December 31 of the given day\'s year', () => {
        const eoy = endOfYear(new Date(2026, 3, 1))
        expect(eoy.getFullYear()).toBe(2026)
        expect(eoy.getMonth()).toBe(11)
        expect(eoy.getDate()).toBe(31)
    })
})

describe('computeAge', () => {
    it('is empty for an unreadable date', () => {
        expect(computeAge('', 'now')).toBe('')
        expect(computeAge('soon', 'end_of_year')).toBe('')
    })

    it('end_of_year never undercuts now', () => {
        const now = Number(computeAge('2016-11-03', 'now'))
        const eoy = Number(computeAge('2016-11-03', 'end_of_year'))
        expect(eoy).toBeGreaterThanOrEqual(now)
    })
})
