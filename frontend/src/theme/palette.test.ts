/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {activeModeVariables, resolveThemeColors, variablesCss} from './palette'
import {DEFAULT_THEME, THEMES} from './themes'

/**
 * The one place a theme key becomes the variables the page is painted with.
 *
 * @vitest-environment node
 */
describe('palette', () => {
    it('falls back to the stock theme for a key nobody knows', () => {
        expect(resolveThemeColors('no-such-theme', null)).toBe(DEFAULT_THEME.colors)
    })

    it('takes the station colours for a custom theme only when it has some', () => {
        const custom = THEMES.midnight!.colors

        expect(resolveThemeColors('custom', custom)).toBe(custom)
        expect(resolveThemeColors('custom', null)).toBe(DEFAULT_THEME.colors)
    })

    it('paints the mode that is showing', () => {
        const colors = DEFAULT_THEME.colors

        expect(activeModeVariables(colors, true)['--color-primary']).toBe(colors.dark.primary)
        expect(activeModeVariables(colors, false)['--color-primary']).toBe(colors.light.primary)
    })

    it('writes variables as the body of a rule', () => {
        expect(variablesCss({'--a': '1', '--b': '2'})).toBe('--a:1;--b:2')
    })
})
