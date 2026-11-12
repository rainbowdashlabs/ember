/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {nonNegative, offsetHoursOf, offsetParts} from './signUpRules'

describe('signUpRules', () => {
    it('takes a count as a whole number of zero or more', () => {
        expect(nonNegative(3)).toBe(3)
        expect(nonNegative(-2)).toBe(0)
        expect(nonNegative(2.7)).toBe(2)
        expect(nonNegative('')).toBeUndefined()
        expect(nonNegative(undefined)).toBeUndefined()
    })

    it('splits an offset into days and hours and joins them again', () => {
        expect(offsetParts(53)).toEqual({days: 2, hours: 5})
        expect(offsetHoursOf(2, 5)).toBe(53)
        expect(offsetHoursOf('', 6)).toBe(6)
        expect(offsetHoursOf(-1, -3)).toBe(0)
    })
})
