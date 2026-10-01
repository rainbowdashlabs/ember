/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { readonly, watch } from 'vue'
import { THEMES, DarkMode, Feel, type ThemeColors, type DarkModeValue, type FeelValue } from '@/theme/themes'
import { activeModeVariables, applyVariables, backgroundVariables, feelVariables, resolveThemeColors } from '@/theme/palette'
import { getItem, setItem } from '@/api/storage'
import { hasSessionCookie } from '@/api/sessionCookie'
import { userSettings } from '@/api'
import type { ThemeInfo } from '@/api/generated/schema'
import { usePride } from '@/composables/usePride'
import { sessionInfo } from '@/util/sessionState'
import { themeRepainted } from '@/util/themeState'
import { browserRef } from '@/util/browserState'
import { reportCaughtError } from '@/util/devErrorReporter'

/** What a public station's colours replaced, to be put back when the reader leaves its pages. */
interface ReplacedTheme {
    theme: string
    feel: FeelValue
    customColors: ThemeColors | null
}

/** Whether a public station's colours are showing, and what they replaced. */
interface StationOverride {
    active: boolean
    previous: ReplacedTheme | null
}

/**
 * The theme the reader sees and what decides it, held per request.
 *
 * <p>Every value starts at the stock theme, the same on the server and in the browser; what this
 * browser has stored is applied by the client plugin, never read here.
 */
function themeState() {
    return {
        activeTheme: useState<string>('useTheme.activeTheme', () => 'ember'),
        activeFeel: useState<FeelValue>('useTheme.activeFeel', () => 'ROUNDED'),
        darkMode: useState<DarkModeValue>('useTheme.darkMode', () => 'system'),
        allowUserTheme: useState<boolean>('useTheme.allowUserTheme', () => true),
        allowUserFeel: useState<boolean>('useTheme.allowUserFeel', () => true),
        stationDefaultTheme: useState<string>('useTheme.stationDefaultTheme', () => 'ember'),
        instanceTheme: useState<string>('useTheme.instanceTheme', () => 'ember'),
        instanceFeel: useState<FeelValue>('useTheme.instanceFeel', () => 'ROUNDED'),
        customThemeColors: useState<ThemeColors | null>('useTheme.customThemeColors', () => null),
        override: useState<StationOverride>('useTheme.override', () => ({active: false, previous: null})),
    }
}

type ThemeState = ReturnType<typeof themeState>

/** Whether this browser has asked for the instance theme yet; it asks once per visit. */
const publicThemeRequested = browserRef(false)

function isDarkActive(): boolean {
    return document.documentElement.classList.contains('dark')
}

function applyTheme(state: ThemeState, themeKey: string) {
    const colors = resolveThemeColors(themeKey, state.customThemeColors.value)
    applyVariables(backgroundVariables(colors))
    applyModeColors(state, colors)
}

function applyModeColors(state: ThemeState, themeColors?: ThemeColors) {
    const colors = themeColors ?? resolveThemeColors(state.activeTheme.value, state.customThemeColors.value)
    applyVariables(activeModeVariables(colors, isDarkActive()))
    themeRepainted()
}

function applyFeel(feel: FeelValue) {
    applyVariables(feelVariables(feel))
}

function resolveEffectiveFeel(feel: FeelValue, themeKey: string): FeelValue {
    const theme = THEMES[themeKey]
    if (theme && !theme.supportedFeels.includes(feel)) {
        return theme.supportedFeels[0] ?? Feel.ROUNDED
    }
    return feel
}

function applyDarkModeClass(mode: DarkModeValue) {
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
    themeRepainted()
}

function applyDarkMode(state: ThemeState, mode: DarkModeValue) {
    applyDarkModeClass(mode)
    applyModeColors(state)
}

function storedDarkMode(hasSession: boolean): DarkModeValue | null {
    const saved = (hasSession ? getItem('dark_mode') : null) as DarkModeValue | null
    if (saved) return saved
    const legacy = hasSession ? getItem('theme') : null
    return legacy === 'dark' || legacy === 'light' ? legacy : null
}

function storedFeel(hasSession: boolean): FeelValue | null {
    const saved = (hasSession ? getItem('feel') : null) as FeelValue | null
    if (saved && Object.values(Feel).includes(saved)) return saved
    const cached = getItem('instance_feel') as FeelValue | null
    return cached && Object.values(Feel).includes(cached) ? cached : null
}

function storedThemeName(hasSession: boolean): string | null {
    const saved = hasSession ? getItem('theme_name') : null
    if (saved && THEMES[saved]) return saved
    const cached = getItem('instance_theme')
    return cached && THEMES[cached] ? cached : null
}

/**
 * Applies the theme available from local storage (user theme, falling back to the cached
 * instance theme) and refreshes the instance theme from the server. When nothing is stored
 * locally, no inline styles are written so the server-rendered theme style stays visible
 * until the instance theme arrives - avoiding a stock-theme flash on first paint.
 */
function initFromLocalStorage(state: ThemeState) {
    const hasSession = hasSessionCookie()

    const savedDarkMode = storedDarkMode(hasSession)
    if (savedDarkMode) state.darkMode.value = savedDarkMode
    applyDarkModeClass(state.darkMode.value)

    const savedFeel = storedFeel(hasSession)
    if (savedFeel) state.activeFeel.value = savedFeel

    const knownTheme = storedThemeName(hasSession)
    if (knownTheme) {
        state.activeTheme.value = knownTheme
        applyTheme(state, knownTheme)
        const effectiveFeel = resolveEffectiveFeel(state.activeFeel.value, knownTheme)
        state.activeFeel.value = effectiveFeel
        applyFeel(effectiveFeel)
    }

    return fetchPublicTheme(state)
}

async function fetchPublicTheme(state: ThemeState) {
    if (publicThemeRequested.value) return
    publicThemeRequested.value = true

    const pride = usePride()
    const hasSession = hasSessionCookie()
    try {
        const { getPublicTheme } = await import('@/api/adminSettings')
        const pub = await getPublicTheme()
        state.instanceTheme.value = pub.defaultTheme
        state.instanceFeel.value = (pub.defaultFeel ?? 'ROUNDED') as FeelValue
        pride.setForcePrideFlag(pub.forcePrideFlag ?? false)

        setItem('instance_theme', pub.defaultTheme)
        setItem('instance_feel', pub.defaultFeel ?? 'ROUNDED')

        const savedTheme = hasSession ? getItem('theme_name') : null
        if (!state.override.value.active && (!savedTheme || !THEMES[savedTheme])) {
            state.activeTheme.value = pub.defaultTheme
            applyTheme(state, pub.defaultTheme)
            const feel = resolveEffectiveFeel(state.instanceFeel.value, pub.defaultTheme)
            state.activeFeel.value = feel
            applyFeel(feel)
        }
    } catch {
        /* ignore - server may not be reachable */
    }
}

function applyStationOverride(
    state: ThemeState,
    themeKey: string | null,
    feel: string | null,
    customColorsJson: string | null,
) {
    if (!state.override.value.active) {
        state.override.value = {
            active: true,
            previous: {
                theme: state.activeTheme.value,
                feel: state.activeFeel.value,
                customColors: state.customThemeColors.value,
            },
        }
    }
    if (customColorsJson) applyCustomColors(state, customColorsJson)
    const theme = themeKey ?? state.activeTheme.value
    state.activeTheme.value = theme
    applyTheme(state, theme)
    const resolvedFeel = resolveEffectiveFeel(
        (feel ?? state.activeFeel.value) as FeelValue,
        theme,
    )
    state.activeFeel.value = resolvedFeel
    applyFeel(resolvedFeel)
}

function clearStationOverride(state: ThemeState) {
    const {active, previous} = state.override.value
    if (!active) return
    state.override.value = {active: false, previous: null}
    if (previous) {
        state.customThemeColors.value = previous.customColors
        state.activeTheme.value = previous.theme
        applyTheme(state, previous.theme)
        state.activeFeel.value = previous.feel
        applyFeel(previous.feel)
    }
}

/** Theme precedence: member (when allowed) → station → instance → 'ember'. */
function resolveSessionTheme(state: ThemeState, themeInfo: ThemeInfo): string {
    const instance = themeInfo.instanceDefaultTheme
    const station = state.stationDefaultTheme.value
    const base = station !== 'ember' ? station : instance
    return state.allowUserTheme.value && themeInfo.userTheme ? themeInfo.userTheme : base
}

/** Feel precedence: member (when allowed) → station → instance (unless locked) → rounded. */
function resolveSessionFeel(state: ThemeState, themeInfo: ThemeInfo, resolvedTheme: string): FeelValue {
    const instance: FeelValue = themeInfo.instanceDefaultFeel
    const locked = themeInfo.instanceLockFeel
    const base = locked ? instance : themeInfo.defaultFeel
    const userFeel = themeInfo.userFeel as FeelValue | null
    const userCanSetFeel = !locked && state.allowUserFeel.value
    return resolveEffectiveFeel(userCanSetFeel && userFeel ? userFeel : base, resolvedTheme)
}

function applyCustomColorsFromSession(state: ThemeState, customThemeColorsJson: string) {
    try {
        state.customThemeColors.value = JSON.parse(customThemeColorsJson) as ThemeColors
    } catch (e) {
        reportCaughtError(e, 'session custom theme colors')
    }
}

function initFromSession(state: ThemeState, themeInfo: ThemeInfo | null | undefined) {
    if (!themeInfo) return
    state.stationDefaultTheme.value = themeInfo.defaultTheme
    state.allowUserTheme.value = themeInfo.allowUserTheme
    state.allowUserFeel.value = themeInfo.allowUserFeel
    if (themeInfo.customThemeColors) {
        applyCustomColorsFromSession(state, themeInfo.customThemeColors)
    }

    const resolvedTheme = resolveSessionTheme(state, themeInfo)
    const resolvedFeel = resolveSessionFeel(state, themeInfo, resolvedTheme)
    const resolvedDarkMode = (themeInfo.userDarkMode ?? 'system') as DarkModeValue

    state.activeTheme.value = resolvedTheme
    state.activeFeel.value = resolvedFeel
    state.darkMode.value = resolvedDarkMode
    applyTheme(state, resolvedTheme)
    applyFeel(resolvedFeel)
    applyDarkMode(state, resolvedDarkMode)

    setItem('theme_name', resolvedTheme)
    setItem('dark_mode', resolvedDarkMode)
    setItem('feel', resolvedFeel)
}

async function setTheme(state: ThemeState, themeKey: string) {
    state.activeTheme.value = themeKey
    applyTheme(state, themeKey)
    const effectiveFeel = resolveEffectiveFeel(state.activeFeel.value, themeKey)
    if (effectiveFeel !== state.activeFeel.value) {
        state.activeFeel.value = effectiveFeel
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

async function setFeel(state: ThemeState, feel: FeelValue) {
    const effectiveFeel = resolveEffectiveFeel(feel, state.activeTheme.value)
    state.activeFeel.value = effectiveFeel
    applyFeel(effectiveFeel)
    setItem('feel', effectiveFeel)
    try {
        await userSettings.updateSettings({ feel: effectiveFeel })
    } catch {
        /* ignore */
    }
}

async function setDarkMode(state: ThemeState, mode: DarkModeValue) {
    state.darkMode.value = mode
    applyDarkMode(state, mode)
    setItem('dark_mode', mode)
    try {
        await userSettings.updateSettings({ darkMode: mode })
    } catch {
        /* ignore */
    }
}

function applyCustomColors(state: ThemeState, colorsJson: string) {
    try {
        state.customThemeColors.value = JSON.parse(colorsJson) as ThemeColors
    } catch {
        /* ignore malformed JSON */
    }
}

/** Forgets the member's and the station's choices and paints the instance theme again. */
function resetToInstanceDefaults(state: ThemeState) {
    setItem('theme_name', '')
    setItem('dark_mode', '')
    setItem('feel', '')

    state.activeTheme.value = state.instanceTheme.value
    state.activeFeel.value = resolveEffectiveFeel(state.instanceFeel.value, state.instanceTheme.value)
    state.darkMode.value = 'system' as DarkModeValue
    state.allowUserTheme.value = true
    state.allowUserFeel.value = true
    state.stationDefaultTheme.value = 'ember'
    state.customThemeColors.value = null

    applyTheme(state, state.activeTheme.value)
    applyFeel(state.activeFeel.value)
    applyDarkMode(state, state.darkMode.value)
}

/**
 * Applies the theme the server reports for the signed-in session whenever that session
 * changes - sign-in, session refresh and station switch all go through it. Wired once
 * during app bootstrap, so reading the session carries no hidden theme side effect.
 */
export function syncThemeWithSession() {
    const state = themeState()
    watch(sessionInfo, info => initFromSession(state, info?.theme), {flush: 'sync', immediate: true})
}

/**
 * The reader's theme, exposed read-only with the operations that change it.
 *
 * <p>Take it at the top of a setup or a composable, before anything is awaited: the state behind it
 * is held per request.
 */
export function useTheme() {
    const state = themeState()
    return {
        activeTheme: readonly(state.activeTheme),
        activeFeel: readonly(state.activeFeel),
        darkMode: readonly(state.darkMode),
        allowUserTheme: readonly(state.allowUserTheme),
        allowUserFeel: readonly(state.allowUserFeel),
        stationDefaultTheme: readonly(state.stationDefaultTheme),
        customThemeColors: readonly(state.customThemeColors),
        applyTheme: (themeKey: string) => applyTheme(state, themeKey),
        applyFeel,
        applyCustomColors: (colorsJson: string) => applyCustomColors(state, colorsJson),
        applyStationOverride: (themeKey: string | null, feel: string | null, customColorsJson: string | null) =>
            applyStationOverride(state, themeKey, feel, customColorsJson),
        clearStationOverride: () => clearStationOverride(state),
        applyDarkMode: (mode: DarkModeValue) => applyDarkMode(state, mode),
        resolveEffectiveFeel,
        initFromLocalStorage: () => initFromLocalStorage(state),
        initFromSession: (themeInfo: ThemeInfo | null | undefined) => initFromSession(state, themeInfo),
        setTheme: (themeKey: string) => setTheme(state, themeKey),
        setFeel: (feel: FeelValue) => setFeel(state, feel),
        setDarkMode: (mode: DarkModeValue) => setDarkMode(state, mode),
        resetToInstanceDefaults: () => resetToInstanceDefaults(state),
    }
}
