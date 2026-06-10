/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, readonly } from 'vue'
import { THEMES, DarkMode, Feel, FEEL_RADIUS, type ThemeColors, type ModeColors, type DarkModeValue, type FeelValue } from '@/theme/themes'
import { contrastTextColor, ensureContrast } from '@/theme/contrast'
import { getItem, setItem } from '@/api/storage'
import { userSettings } from '@/api'
import { usePride } from '@/composables/usePride'

const activeTheme = ref<string>('ember')
const activeFeel = ref<FeelValue>(Feel.ROUNDED)
const darkMode = ref<DarkModeValue>('system')
const allowUserTheme = ref(true)
const allowUserFeel = ref(true)
const stationDefaultTheme = ref('ember')
const instanceTheme = ref('ember')
const instanceFeel = ref<FeelValue>(Feel.ROUNDED)
const customThemeColors = ref<ThemeColors | null>(null)

function isDarkActive(): boolean {
    return document.documentElement.classList.contains('dark')
}

function resolveCurrentThemeColors(): ThemeColors {
    const key = activeTheme.value
    if (key === 'custom' && customThemeColors.value) return customThemeColors.value
    return THEMES[key]?.colors ?? THEMES.ember.colors
}

function resolveModeColors(themeColors: ThemeColors): ModeColors {
    return isDarkActive() ? themeColors.dark : themeColors.light
}

function applyTheme(themeKey: string) {
    const colors =
        themeKey === 'custom' && customThemeColors.value
            ? customThemeColors.value
            : (THEMES[themeKey]?.colors ?? THEMES.ember.colors)
    const root = document.documentElement.style
    root.setProperty('--color-bg-light', colors.bgLight)
    root.setProperty('--color-bg-light-accent', colors.bgLightAccent)
    root.setProperty('--color-bg-dark', colors.bgDark)
    root.setProperty('--color-bg-dark-accent', colors.bgDarkAccent)

    applyModeColors(colors)
}

function applyModeColors(themeColors?: ThemeColors) {
    const colors = themeColors ?? resolveCurrentThemeColors()
    const mode = resolveModeColors(colors)
    const root = document.documentElement.style

    root.setProperty('--color-primary', mode.primary)
    root.setProperty('--color-primary-accent', mode.primaryAccent)
    root.setProperty('--color-secondary', mode.secondary)
    root.setProperty('--color-secondary-accent', mode.secondaryAccent)
    root.setProperty('--color-info', mode.info)
    root.setProperty('--color-info-accent', mode.infoAccent)
    root.setProperty('--color-success', mode.success)
    root.setProperty('--color-error', mode.error)

    root.setProperty('--color-primary-text', contrastTextColor(mode.primary))
    root.setProperty('--color-primary-accent-text', contrastTextColor(mode.primaryAccent))
    root.setProperty('--color-secondary-text', contrastTextColor(mode.secondary))
    root.setProperty('--color-secondary-accent-text', contrastTextColor(mode.secondaryAccent))
    root.setProperty('--color-info-text', contrastTextColor(mode.info))
    root.setProperty('--color-info-accent-text', contrastTextColor(mode.infoAccent))
    root.setProperty('--color-success-text', contrastTextColor(mode.success))
    root.setProperty('--color-error-text', contrastTextColor(mode.error))

    const pageBg = isDarkActive() ? colors.bgDark : colors.bgLight
    root.setProperty('--color-primary-badge', ensureContrast(mode.primaryAccent, pageBg))
    root.setProperty('--color-secondary-badge', ensureContrast(mode.secondaryAccent, pageBg))
    root.setProperty('--color-info-badge', ensureContrast(mode.infoAccent, pageBg))
    root.setProperty('--color-success-badge', ensureContrast(mode.success, pageBg))
    root.setProperty('--color-error-badge', ensureContrast(mode.error, pageBg))
}

function applyFeel(feel: FeelValue) {
    document.documentElement.style.setProperty('--radius-theme', FEEL_RADIUS[feel] ?? FEEL_RADIUS[Feel.ROUNDED])
}

function resolveEffectiveFeel(feel: FeelValue, themeKey: string): FeelValue {
    const theme = THEMES[themeKey]
    if (theme && !theme.supportedFeels.includes(feel)) {
        return theme.supportedFeels[0] ?? Feel.ROUNDED
    }
    return feel
}

function applyDarkMode(mode: DarkModeValue) {
    const html = document.documentElement
    html.classList.remove('dark', 'light')
    if (mode === DarkMode.DARK) {
        html.classList.add('dark')
    } else if (mode === DarkMode.LIGHT) {
        html.classList.add('light')
    } else {
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
        html.classList.add(prefersDark ? 'dark' : 'light')
    }
    applyModeColors()
}

function initFromLocalStorage() {
    const savedTheme = getItem('theme_name')
    const savedDarkMode = getItem('dark_mode') as DarkModeValue | null
    const savedFeel = getItem('feel') as FeelValue | null
    if (savedTheme && THEMES[savedTheme]) {
        activeTheme.value = savedTheme
    }
    applyTheme(activeTheme.value)

    if (savedFeel && Object.values(Feel).includes(savedFeel)) {
        activeFeel.value = savedFeel
    }
    const effectiveFeel = resolveEffectiveFeel(activeFeel.value, activeTheme.value)
    activeFeel.value = effectiveFeel
    applyFeel(effectiveFeel)

    if (savedDarkMode) {
        darkMode.value = savedDarkMode
    } else {
        const old = getItem('theme')
        if (old === 'dark' || old === 'light') {
            darkMode.value = old
        }
    }
    applyDarkMode(darkMode.value)

    // Always fetch instance defaults — applies as baseline for unauthenticated pages,
    // and will be overridden by initFromSession when the user logs in.
    fetchPublicTheme()
}

let publicThemeFetched = false

async function fetchPublicTheme() {
    if (publicThemeFetched) return
    publicThemeFetched = true

    // Apply cached instance theme immediately to avoid flash
    const cachedTheme = getItem('instance_theme')
    const cachedFeel = getItem('instance_feel') as FeelValue | null
    if (cachedTheme && !getItem('theme_name')) {
        activeTheme.value = cachedTheme
        applyTheme(cachedTheme)
        if (cachedFeel) {
            const feel = resolveEffectiveFeel(cachedFeel, cachedTheme)
            activeFeel.value = feel
            applyFeel(feel)
        }
    }

    try {
        const { getPublicTheme } = await import('@/api/adminSettings')
        const pub = await getPublicTheme()
        instanceTheme.value = pub.defaultTheme
        instanceFeel.value = (pub.defaultFeel ?? 'ROUNDED') as FeelValue
        usePride().setForcePrideFlag(pub.forcePrideFlag ?? false)

        setItem('instance_theme', pub.defaultTheme)
        setItem('instance_feel', pub.defaultFeel ?? 'ROUNDED')

        const savedTheme = getItem('theme_name')
        if (!savedTheme || !THEMES[savedTheme]) {
            activeTheme.value = pub.defaultTheme
            applyTheme(pub.defaultTheme)
            const feel = resolveEffectiveFeel(instanceFeel.value, pub.defaultTheme)
            activeFeel.value = feel
            applyFeel(feel)
        }
    } catch {
        /* ignore — server may not be reachable */
    }
}

function initFromSession(
    themeInfo: {
        instanceDefaultTheme?: string
        instanceDefaultFeel?: string
        instanceLockFeel?: boolean
        defaultTheme?: string
        defaultFeel?: string
        allowUserTheme?: boolean
        allowUserFeel?: boolean
        customThemeColors?: string | null
        userTheme?: string
        userDarkMode?: string
        userFeel?: string
    } | null | undefined,
) {
    if (!themeInfo) return
    stationDefaultTheme.value = themeInfo.defaultTheme ?? 'ember'
    allowUserTheme.value = themeInfo.allowUserTheme ?? true
    allowUserFeel.value = themeInfo.allowUserFeel ?? true
    if (themeInfo.customThemeColors) {
        try {
            customThemeColors.value = JSON.parse(themeInfo.customThemeColors) as ThemeColors
        } catch {
            /* ignore malformed JSON */
        }
    }

    // Theme resolution: user (if allowed) → station → instance → 'ember'
    const instanceTheme = themeInfo.instanceDefaultTheme ?? 'ember'
    const baseTheme = stationDefaultTheme.value !== 'ember' ? stationDefaultTheme.value : instanceTheme
    const resolvedTheme = allowUserTheme.value && themeInfo.userTheme ? themeInfo.userTheme : baseTheme
    const resolvedDarkMode = (themeInfo.userDarkMode ?? 'system') as DarkModeValue

    // Feel resolution: user (if allowed) → station → instance (if not locked) → 'ROUNDED'
    const instanceFeel = (themeInfo.instanceDefaultFeel ?? 'ROUNDED') as FeelValue
    const instanceLockFeel = themeInfo.instanceLockFeel ?? false
    const stationFeel = (themeInfo.defaultFeel ?? null) as FeelValue | null
    const baseFeel = instanceLockFeel
        ? instanceFeel
        : (stationFeel ?? instanceFeel)
    const userCanSetFeel = !instanceLockFeel && allowUserFeel.value
    const userFeel = themeInfo.userFeel as FeelValue | null
    const resolvedFeel = resolveEffectiveFeel(
        userCanSetFeel && userFeel ? userFeel : baseFeel,
        resolvedTheme,
    )

    activeTheme.value = resolvedTheme
    activeFeel.value = resolvedFeel
    darkMode.value = resolvedDarkMode
    applyTheme(resolvedTheme)
    applyFeel(resolvedFeel)
    applyDarkMode(resolvedDarkMode)

    setItem('theme_name', resolvedTheme)
    setItem('dark_mode', resolvedDarkMode)
    setItem('feel', resolvedFeel)
}

async function setTheme(themeKey: string) {
    activeTheme.value = themeKey
    applyTheme(themeKey)
    // If current feel is not supported by new theme, switch feel too
    const effectiveFeel = resolveEffectiveFeel(activeFeel.value, themeKey)
    if (effectiveFeel !== activeFeel.value) {
        activeFeel.value = effectiveFeel
        applyFeel(effectiveFeel)
        setItem('feel', effectiveFeel)
    }
    setItem('theme_name', themeKey)
    try {
        await userSettings.updateSettings({ theme: themeKey, feel: effectiveFeel })
    } catch {
        /* ignore */
    }
}

async function setFeel(feel: FeelValue) {
    const effectiveFeel = resolveEffectiveFeel(feel, activeTheme.value)
    activeFeel.value = effectiveFeel
    applyFeel(effectiveFeel)
    setItem('feel', effectiveFeel)
    try {
        await userSettings.updateSettings({ feel: effectiveFeel })
    } catch {
        /* ignore */
    }
}

async function setDarkMode(mode: DarkModeValue) {
    darkMode.value = mode
    applyDarkMode(mode)
    setItem('dark_mode', mode)
    try {
        await userSettings.updateSettings({ darkMode: mode })
    } catch {
        /* ignore */
    }
}

function applyCustomColors(colorsJson: string) {
    try {
        customThemeColors.value = JSON.parse(colorsJson) as ThemeColors
    } catch {
        /* ignore malformed JSON */
    }
}

function resetToInstanceDefaults() {
    // Clear user/station overrides from localStorage
    setItem('theme_name', '')
    setItem('dark_mode', '')
    setItem('feel', '')

    // Apply instance defaults
    activeTheme.value = instanceTheme.value
    activeFeel.value = resolveEffectiveFeel(instanceFeel.value, instanceTheme.value)
    darkMode.value = 'system' as DarkModeValue
    allowUserTheme.value = true
    allowUserFeel.value = true
    stationDefaultTheme.value = 'ember'
    customThemeColors.value = null

    applyTheme(activeTheme.value)
    applyFeel(activeFeel.value)
    applyDarkMode(darkMode.value)
}

export function useTheme() {
    return {
        activeTheme: readonly(activeTheme),
        activeFeel: readonly(activeFeel),
        darkMode: readonly(darkMode),
        allowUserTheme: readonly(allowUserTheme),
        allowUserFeel: readonly(allowUserFeel),
        stationDefaultTheme: readonly(stationDefaultTheme),
        customThemeColors: readonly(customThemeColors),
        applyTheme,
        applyFeel,
        applyCustomColors,
        applyDarkMode,
        resolveEffectiveFeel,
        initFromLocalStorage,
        initFromSession,
        setTheme,
        setFeel,
        setDarkMode,
        resetToInstanceDefaults,
    }
}
