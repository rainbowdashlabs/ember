/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {compositeOver, contrastingTextColor, parseCssColor} from './contrastColor'

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
