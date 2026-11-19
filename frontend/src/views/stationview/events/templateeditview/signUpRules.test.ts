/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {nonNegative} from './signUpRules'

describe('signUpRules', () => {
    it('takes a count as a whole number of zero or more', () => {
        expect(nonNegative(3)).toBe(3)
        expect(nonNegative(-2)).toBe(0)
        expect(nonNegative(2.7)).toBe(2)
        expect(nonNegative('')).toBeUndefined()
        expect(nonNegative(undefined)).toBeUndefined()
    })
})
