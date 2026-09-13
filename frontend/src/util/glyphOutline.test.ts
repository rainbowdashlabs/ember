/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it} from 'vitest'
import {forgetGlyphSurfaces, outlineFor, surfaceColor} from './glyphOutline'

function paintPage(bg: string, accent: string, primary = '#ff6421') {
    document.documentElement.style.setProperty('--bg', bg)
    document.documentElement.style.setProperty('--bg-accent', accent)
    document.documentElement.style.setProperty('--color-primary', primary)
    forgetGlyphSurfaces()
}

describe('outlineFor', () => {
    beforeEach(() => {
        paintPage('#ffffff', '#e5e7eb')
    })

    it('leaves a colour that carries itself alone', () => {
        expect(outlineFor('#1d4ed8', 'page')).toBeNull()
        expect(outlineFor('#b91c1c', 'page')).toBeNull()
    })

    it('strokes a colour too close to the surface behind it', () => {
        expect(outlineFor('#fde68a', 'page')).toBe('#1a1a1a')
        expect(outlineFor('#f5f5f5', 'page')).toBe('#1a1a1a')
    })

    it('answers per surface, because a selected row is painted differently', () => {
        paintPage('#111827', '#f3f4f6')

        expect(outlineFor('#fde68a', 'page')).toBeNull()
        expect(outlineFor('#fde68a', 'accent')).toBe('#1a1a1a')
    })

    it('strokes in white where the surface is dark', () => {
        paintPage('#111827', '#1f2937')

        expect(outlineFor('#1e3a8a', 'page')).toBe('#ffffff')
    })

    it('needs nothing for a glyph with no colour of its own', () => {
        expect(outlineFor(null, 'page')).toBeNull()
        expect(outlineFor(undefined, 'accent')).toBeNull()
        expect(outlineFor('', 'page')).toBeNull()
    })

    it('says nothing about a colour it cannot read', () => {
        expect(outlineFor('rebeccapurple', 'page')).toBeNull()
    })

    /** The highlight is the page tinted by the primary colour, which is what a picker row paints. */
    it('measures the highlight between the page and the primary colour', () => {
        paintPage('#ffffff', '#e5e7eb', '#000000')

        const [r, g, b] = surfaceColor('highlight')

        expect(r).toBeLessThan(255)
        expect(r).toBe(g)
        expect(g).toBe(b)
    })

    it('forgets what it measured when the theme repaints', () => {
        expect(outlineFor('#fde68a', 'page')).toBe('#1a1a1a')

        paintPage('#111827', '#1f2937')

        expect(outlineFor('#fde68a', 'page')).toBeNull()
    })
})
