/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {contrastTextColor, ensureContrast} from '../util/contrastColor'
import {DEFAULT_THEME, Feel, FEEL_RADIUS, THEMES, type FeelValue, type ModeColors, type ThemeColors} from './themes'

/** CSS custom properties by name, each with the value it takes. */
export type ThemeVariables = Record<string, string>

/**
 * The colours a theme key stands for: the station's own colours for `custom` when it has them, the
 * stock theme for a key nobody knows.
 */
export function resolveThemeColors(themeKey: string, customColors: ThemeColors | null): ThemeColors {
    if (themeKey === 'custom' && customColors) return customColors
    return THEMES[themeKey]?.colors ?? DEFAULT_THEME.colors
}

/** The page backgrounds of both modes, which do not change with the mode that is showing. */
export function backgroundVariables(colors: ThemeColors): ThemeVariables {
    return {
        '--color-bg-light': colors.bgLight,
        '--color-bg-light-accent': colors.bgLightAccent,
        '--color-bg-dark': colors.bgDark,
        '--color-bg-dark-accent': colors.bgDarkAccent,
    }
}

/** The corner radius a feel stands for. */
export function feelVariables(feel: FeelValue): ThemeVariables {
    return {'--radius-theme': FEEL_RADIUS[feel] ?? FEEL_RADIUS[Feel.ROUNDED]}
}

/**
 * The colours of one mode together with the letters that sit on them and the badge colours that
 * stay readable on its page background.
 *
 * <p>The browser and the server both paint a theme from this, so the first paint the server sends
 * and the page the browser repaints can never disagree about a single variable.
 *
 * @param mode   the colours of the light or the dark mode
 * @param pageBg the page background of that mode
 */
export function modeVariables(mode: ModeColors, pageBg: string): ThemeVariables {
    return {
        '--color-primary': mode.primary,
        '--color-primary-accent': mode.primaryAccent,
        '--color-secondary': mode.secondary,
        '--color-secondary-accent': mode.secondaryAccent,
        '--color-info': mode.info,
        '--color-info-accent': mode.infoAccent,
        '--color-success': mode.success,
        '--color-error': mode.error,
        '--color-primary-text': contrastTextColor(mode.primary),
        '--color-primary-accent-text': contrastTextColor(mode.primaryAccent),
        '--color-secondary-text': contrastTextColor(mode.secondary),
        '--color-secondary-accent-text': contrastTextColor(mode.secondaryAccent),
        '--color-info-text': contrastTextColor(mode.info),
        '--color-info-accent-text': contrastTextColor(mode.infoAccent),
        '--color-success-text': contrastTextColor(mode.success),
        '--color-error-text': contrastTextColor(mode.error),
        '--color-primary-badge': ensureContrast(mode.primaryAccent, pageBg),
        '--color-secondary-badge': ensureContrast(mode.secondaryAccent, pageBg),
        '--color-info-badge': ensureContrast(mode.infoAccent, pageBg),
        '--color-success-badge': ensureContrast(mode.success, pageBg),
        '--color-error-badge': ensureContrast(mode.error, pageBg),
    }
}

/** The variables of the mode that is showing, light or dark. */
export function activeModeVariables(colors: ThemeColors, dark: boolean): ThemeVariables {
    return dark ? modeVariables(colors.dark, colors.bgDark) : modeVariables(colors.light, colors.bgLight)
}

/** Writes variables onto the document root, where every themed style reads them. */
export function applyVariables(variables: ThemeVariables) {
    const root = document.documentElement.style
    for (const [name, value] of Object.entries(variables)) {
        root.setProperty(name, value)
    }
}

/** Variables as the body of a CSS rule. */
export function variablesCss(variables: ThemeVariables): string {
    return Object.entries(variables).map(([name, value]) => `${name}:${value}`).join(';')
}
