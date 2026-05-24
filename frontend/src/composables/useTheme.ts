/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, readonly } from 'vue'
import { THEMES, DarkMode, type ThemeColors, type DarkModeValue } from '@/theme/themes'
import { getItem, setItem } from '@/api/storage'
import { userSettings } from '@/api'

const activeTheme = ref<string>('ember')
const darkMode = ref<DarkModeValue>('system')
const allowUserTheme = ref(true)
const stationDefaultTheme = ref('ember')
const customThemeColors = ref<ThemeColors | null>(null)

function applyTheme(themeKey: string) {
    const colors =
        themeKey === 'custom' && customThemeColors.value
            ? customThemeColors.value
            : (THEMES[themeKey]?.colors ?? THEMES.ember.colors)
    const root = document.documentElement.style
    root.setProperty('--color-primary', colors.primary)
    root.setProperty('--color-primary-accent', colors.primaryAccent)
    root.setProperty('--color-secondary', colors.secondary)
    root.setProperty('--color-secondary-accent', colors.secondaryAccent)
    root.setProperty('--color-info', colors.info)
    root.setProperty('--color-info-accent', colors.infoAccent)
    root.setProperty('--color-success', colors.success)
    root.setProperty('--color-error', colors.error)
    root.setProperty('--color-bg-light', colors.bgLight)
    root.setProperty('--color-bg-light-accent', colors.bgLightAccent)
    root.setProperty('--color-bg-dark', colors.bgDark)
    root.setProperty('--color-bg-dark-accent', colors.bgDarkAccent)
}

function applyDarkMode(mode: DarkModeValue) {
    const html = document.documentElement
    html.classList.remove('dark', 'light')
    if (mode === DarkMode.DARK) {
        html.classList.add('dark')
    } else if (mode === DarkMode.LIGHT) {
        html.classList.add('light')
    } else {
        // System mode: apply class based on system preference so Tailwind dark: classes work
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
        html.classList.add(prefersDark ? 'dark' : 'light')
    }
}

function initFromLocalStorage() {
    const savedTheme = getItem('theme_name')
    const savedDarkMode = getItem('dark_mode') as DarkModeValue | null
    if (savedTheme && THEMES[savedTheme]) {
        activeTheme.value = savedTheme
    }
    // Always apply theme colors (defaults to 'ember' if nothing saved)
    applyTheme(activeTheme.value)

    if (savedDarkMode) {
        darkMode.value = savedDarkMode
    } else {
        // Legacy: check old 'theme' key (was 'dark'/'light')
        const old = getItem('theme')
        if (old === 'dark' || old === 'light') {
            darkMode.value = old
        }
    }
    // Always apply dark mode (defaults to 'system' if nothing saved)
    applyDarkMode(darkMode.value)
}

function initFromSession(
    themeInfo: {
        defaultTheme?: string
        allowUserTheme?: boolean
        customThemeColors?: string | null
        userTheme?: string
        userDarkMode?: string
    } | null | undefined,
) {
    if (!themeInfo) return
    stationDefaultTheme.value = themeInfo.defaultTheme ?? 'ember'
    allowUserTheme.value = themeInfo.allowUserTheme ?? true
    if (themeInfo.customThemeColors) {
        try {
            customThemeColors.value = JSON.parse(themeInfo.customThemeColors)
        } catch {
            /* ignore malformed JSON */
        }
    }

    const resolvedTheme = allowUserTheme.value && themeInfo.userTheme ? themeInfo.userTheme : stationDefaultTheme.value
    const resolvedDarkMode = (themeInfo.userDarkMode ?? 'system') as DarkModeValue

    activeTheme.value = resolvedTheme
    darkMode.value = resolvedDarkMode
    applyTheme(resolvedTheme)
    applyDarkMode(resolvedDarkMode)

    setItem('theme_name', resolvedTheme)
    setItem('dark_mode', resolvedDarkMode)
}

async function setTheme(themeKey: string) {
    activeTheme.value = themeKey
    applyTheme(themeKey)
    setItem('theme_name', themeKey)
    try {
        await userSettings.updateSettings({ theme: themeKey })
    } catch {
        /* ignore — server may not support theme field yet */
    }
}

async function setDarkMode(mode: DarkModeValue) {
    darkMode.value = mode
    applyDarkMode(mode)
    setItem('dark_mode', mode)
    try {
        await userSettings.updateSettings({ darkMode: mode })
    } catch {
        /* ignore — server may not support darkMode field yet */
    }
}

export function useTheme() {
    return {
        activeTheme: readonly(activeTheme),
        darkMode: readonly(darkMode),
        allowUserTheme: readonly(allowUserTheme),
        stationDefaultTheme: readonly(stationDefaultTheme),
        customThemeColors: readonly(customThemeColors),
        applyTheme,
        applyDarkMode,
        initFromLocalStorage,
        initFromSession,
        setTheme,
        setDarkMode,
    }
}
