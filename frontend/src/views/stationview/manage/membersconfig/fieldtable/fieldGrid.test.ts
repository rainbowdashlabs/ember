/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {fieldGridMinWidthRem, fitsFieldGrid} from './fieldGrid'

describe('fieldGridMinWidthRem', () => {
    it('sums the fixed columns, a readable name and the row padding', () => {
        expect(fieldGridMinWidthRem(false)).toBe(26.5 + 10 + 0.5)
    })

    it('makes room for the writability choice when it is shown', () => {
        expect(fieldGridMinWidthRem(true)).toBe(35 + 10 + 0.5)
    })
})

describe('fitsFieldGrid', () => {
    it('fits the plain grid from its threshold on', () => {
        expect(fitsFieldGrid(36.9, false)).toBe(false)
        expect(fitsFieldGrid(37, false)).toBe(true)
    })

    it('needs more room once the writability choice is shown', () => {
        expect(fitsFieldGrid(40, false)).toBe(true)
        expect(fitsFieldGrid(40, true)).toBe(false)
        expect(fitsFieldGrid(45.5, true)).toBe(true)
    })
})
