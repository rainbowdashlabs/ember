/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {
    compositeOver,
    contrastingTextColor,
    contrastRatio,
    contrastTextColor,
    ensureContrast,
    parseCssColor,
    parseHexColor,
} from './contrastColor'

describe('parseCssColor', () => {
    it('reads the rgb a browser reports for a plain colour', () => {
        expect(parseCssColor('rgb(240, 80, 80)')).toEqual([240, 80, 80, 1])
    })

    it('reads the alpha of a translucent colour, whichever way it is written', () => {
        expect(parseCssColor('rgba(0, 0, 0, 0.5)')).toEqual([0, 0, 0, 0.5])
        expect(parseCssColor('rgb(0 0 0 / 0.5)')).toEqual([0, 0, 0, 0.5])
    })

    /**
     * The form a mixed colour comes back in. A lightness of 0.82 is a bright yellow, and reading
     * the three numbers as channels would call it near black instead.
     */
    it('reads the oklab a mixed colour resolves to', () => {
        const parsed = parseCssColor('oklab(0.819776 -0.0234922 0.161686 / 0.7)')

        expect(parsed?.[3]).toBe(0.7)
        expect(contrastingTextColor(parsed![0], parsed![1], parsed![2])).toBe('#1a1a1a')
    })

    it('reads the oklch the utility palette is written in', () => {
        const parsed = parseCssColor('oklch(0.623 0.214 259.815)')

        expect(parsed?.[3]).toBe(1)
        expect(contrastingTextColor(parsed![0], parsed![1], parsed![2])).toBe('#ffffff')
    })

    it('reads a hex literal and calls transparent nothing', () => {
        expect(parseCssColor('#f05050')).toEqual([240, 80, 80, 1])
        expect(parseCssColor('transparent')).toEqual([0, 0, 0, 0])
    })

    it('returns nothing for what it cannot read', () => {
        expect(parseCssColor('')).toBeNull()
        expect(parseCssColor('rebeccapurple')).toBeNull()
    })
})

describe('contrastRatio', () => {
    it('is one for a colour against itself and twenty one for black on white', () => {
        expect(contrastRatio([120, 120, 120], [120, 120, 120])).toBeCloseTo(1, 5)
        expect(contrastRatio([0, 0, 0], [255, 255, 255])).toBeCloseTo(21, 1)
    })

    it('does not care which way round the two are given', () => {
        expect(contrastRatio([0, 0, 0], [200, 200, 200])).toBeCloseTo(contrastRatio([200, 200, 200], [0, 0, 0]), 8)
    })

    /** Three to one is the line a graphic has to clear, so the cases either side of it are the ones to hold. */
    it('puts a pale yellow under three against white and a blue above it', () => {
        expect(contrastRatio([253, 230, 138], [255, 255, 255])).toBeLessThan(3)
        expect(contrastRatio([29, 78, 216], [255, 255, 255])).toBeGreaterThan(3)
    })
})

describe('compositeOver', () => {
    it('lets the backdrop through in proportion to the alpha', () => {
        expect(compositeOver([200, 200, 200, 0.5], [0, 0, 0])).toEqual([100, 100, 100])
    })

    /** The same badge over a dark page and a light one is not the same colour to read on. */
    it('answers differently for the same colour on two pages', () => {
        const badge: [number, number, number, number] = [224, 196, 32, 0.7]

        expect(contrastingTextColor(...compositeOver(badge, [31, 31, 31]))).toBe('#ffffff')
        expect(contrastingTextColor(...compositeOver(badge, [229, 229, 229]))).toBe('#1a1a1a')
    })
})

describe('contrastTextColor', () => {
    it('puts dark letters on a bright orange and white ones on a deep blue', () => {
        expect(contrastTextColor('#fd4f00')).toBe('#1a1a1a')
        expect(contrastTextColor('#1858c8')).toBe('#ffffff')
    })

    it('falls back to dark letters for a colour it cannot read', () => {
        expect(contrastTextColor('not a colour')).toBe('#1a1a1a')
    })
})

describe('ensureContrast', () => {
    it('keeps a colour that is already readable', () => {
        expect(ensureContrast('#000000', '#ffffff')).toBe('#000000')
    })

    it('darkens a pale colour on a light page until it reads', () => {
        const adjusted = ensureContrast('#ffdd1b', '#eaeaea')

        expect(adjusted).not.toBe('#ffdd1b')
        expect(contrastRatio(parseHexColor(adjusted)!, [234, 234, 234])).toBeGreaterThanOrEqual(4.5)
    })

    it('lightens a deep colour on a dark page until it reads', () => {
        const adjusted = ensureContrast('#1858c8', '#212121')

        expect(contrastRatio(parseHexColor(adjusted)!, [33, 33, 33])).toBeGreaterThanOrEqual(4.5)
        expect(parseHexColor(adjusted)![2]).toBeGreaterThan(0xc8)
    })
})
