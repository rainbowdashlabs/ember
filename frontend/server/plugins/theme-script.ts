import {
    Feel,
    FEEL_RADIUS,
    THEMES,
    type FeelValue,
    type ModeColors,
    type ThemeColors,
} from '../../src/theme/themes'
import {contrastTextColor, ensureContrast} from '../../src/theme/contrast'

const THEME_SCRIPT = `<script>try{var m=localStorage.getItem('dark_mode')||localStorage.getItem('theme');if(m==='LIGHT'||m==='light'){document.documentElement.classList.add('light')}else if(m==='DARK'||m==='dark'){document.documentElement.classList.add('dark')}else{document.documentElement.classList.add(window.matchMedia('(prefers-color-scheme:dark)').matches?'dark':'light')}}catch(e){}</script>`

const BACKEND_URL = process.env.EMBER_BACKEND_URL || process.env.NUXT_BACKEND_URL || 'http://localhost:8080'
const CACHE_TTL_MS = 60_000
const PUBLIC_STATION_RE = /^\/public\/station\/([^/]+)/
/**
 * A form or a page somebody was sent the link to. The reader holds a link and no station, so the
 * station behind it is asked for by that link. It cannot go through the station information route
 * the line above uses: that one answers nothing for a station with no public site, which is exactly
 * the station most likely to have sent somebody a form.
 */
const SHARED_FORM_RE = /^\/f\/([^/]+)/
const SHARED_PAGE_RE = /^\/s\/([^/]+)/

interface ResolvedTheme {
    theme: string
    feel: FeelValue
    customColors: ThemeColors | null
}

interface CacheEntry {
    data: ResolvedTheme
    expires: number
}

const themeCache = new Map<string, CacheEntry>()

async function fetchJson<T>(url: string): Promise<T | null> {
    try {
        const res = await fetch(url, {signal: AbortSignal.timeout(2000)})
        if (!res.ok) return null
        return (await res.json()) as T
    } catch {
        return null
    }
}

async function resolveInstanceTheme(): Promise<ResolvedTheme | null> {
    const cached = themeCache.get('__instance__')
    if (cached && cached.expires > Date.now()) return cached.data
    const data = await fetchJson<{defaultTheme: string; defaultFeel: string}>(
        `${BACKEND_URL}/api/v1/public/settings/theme`,
    )
    if (!data) return cached?.data ?? null
    const resolved: ResolvedTheme = {
        theme: data.defaultTheme,
        feel: (data.defaultFeel ?? Feel.ROUNDED) as FeelValue,
        customColors: null,
    }
    themeCache.set('__instance__', {data: resolved, expires: Date.now() + CACHE_TTL_MS})
    return resolved
}

async function resolveStationTheme(uid: string): Promise<ResolvedTheme | null> {
    return resolveThemeFrom(uid, `${BACKEND_URL}/api/v1/public/station/${encodeURIComponent(uid)}/info`)
}

/**
 * The station behind a share link, asked for by that link and marking no page hit, so a render does
 * not count as a visit on top of the one the page itself reports. A form's link and a page's link
 * are different tokens on different tables, so each is asked of its own.
 */
async function resolveSharedTheme(kind: 'form' | 'page', token: string): Promise<ResolvedTheme | null> {
    const base = kind === 'form' ? 'shared-form' : 'shared'
    return resolveThemeFrom(
        `shared:${kind}:${token}`,
        `${BACKEND_URL}/api/v1/public/${base}/${encodeURIComponent(token)}/brand`,
    )
}

async function resolveThemeFrom(cacheKey: string, url: string): Promise<ResolvedTheme | null> {
    const cached = themeCache.get(cacheKey)
    if (cached && cached.expires > Date.now()) return cached.data
    const data = await fetchJson<{
        defaultTheme: string | null
        defaultFeel: string | null
        customThemeColors: string | null
    }>(url)
    if (!data) return cached?.data ?? (await resolveInstanceTheme())
    let customColors: ThemeColors | null = null
    if (data.customThemeColors) {
        try {
            customColors = JSON.parse(data.customThemeColors) as ThemeColors
        } catch {
            customColors = null
        }
    }
    const instance = await resolveInstanceTheme()
    const resolved: ResolvedTheme = {
        theme: data.defaultTheme ?? instance?.theme ?? 'ember',
        feel: (data.defaultFeel ?? instance?.feel ?? Feel.ROUNDED) as FeelValue,
        customColors,
    }
    themeCache.set(cacheKey, {data: resolved, expires: Date.now() + CACHE_TTL_MS})
    return resolved
}

function resolveColors(theme: string, customColors: ThemeColors | null): ThemeColors {
    if (theme === 'custom' && customColors) return customColors
    return THEMES[theme]?.colors ?? THEMES.ember.colors
}

function buildModeBlock(mode: ModeColors, pageBg: string): string {
    return [
        `--color-primary:${mode.primary}`,
        `--color-primary-accent:${mode.primaryAccent}`,
        `--color-secondary:${mode.secondary}`,
        `--color-secondary-accent:${mode.secondaryAccent}`,
        `--color-info:${mode.info}`,
        `--color-info-accent:${mode.infoAccent}`,
        `--color-success:${mode.success}`,
        `--color-error:${mode.error}`,
        `--color-primary-text:${contrastTextColor(mode.primary)}`,
        `--color-primary-accent-text:${contrastTextColor(mode.primaryAccent)}`,
        `--color-secondary-text:${contrastTextColor(mode.secondary)}`,
        `--color-secondary-accent-text:${contrastTextColor(mode.secondaryAccent)}`,
        `--color-info-text:${contrastTextColor(mode.info)}`,
        `--color-info-accent-text:${contrastTextColor(mode.infoAccent)}`,
        `--color-success-text:${contrastTextColor(mode.success)}`,
        `--color-error-text:${contrastTextColor(mode.error)}`,
        `--color-primary-badge:${ensureContrast(mode.primaryAccent, pageBg)}`,
        `--color-secondary-badge:${ensureContrast(mode.secondaryAccent, pageBg)}`,
        `--color-info-badge:${ensureContrast(mode.infoAccent, pageBg)}`,
        `--color-success-badge:${ensureContrast(mode.success, pageBg)}`,
        `--color-error-badge:${ensureContrast(mode.error, pageBg)}`,
    ].join(';')
}

function buildStyle(theme: ResolvedTheme): string {
    const colors = resolveColors(theme.theme, theme.customColors)
    const radius = FEEL_RADIUS[theme.feel] ?? FEEL_RADIUS[Feel.ROUNDED]
    const rootBlock = [
        `--color-bg-light:${colors.bgLight}`,
        `--color-bg-light-accent:${colors.bgLightAccent}`,
        `--color-bg-dark:${colors.bgDark}`,
        `--color-bg-dark-accent:${colors.bgDarkAccent}`,
        `--radius-theme:${radius}`,
    ].join(';')
    const lightBlock = buildModeBlock(colors.light, colors.bgLight)
    const darkBlock = buildModeBlock(colors.dark, colors.bgDark)
    return `<style data-ssr-theme>:root{${rootBlock}}.light{${lightBlock}}.dark{${darkBlock}}</style>`
}

export default defineNitroPlugin((nitroApp) => {
    nitroApp.hooks.hook('render:html', async (html, {event}) => {
        html.head.unshift(THEME_SCRIPT)

        const path = event.path ?? ''
        const stationMatch = path.match(PUBLIC_STATION_RE)
        const formMatch = path.match(SHARED_FORM_RE)
        const pageMatch = path.match(SHARED_PAGE_RE)
        let theme: ResolvedTheme | null
        if (stationMatch) {
            theme = await resolveStationTheme(stationMatch[1])
        } else if (formMatch) {
            theme = await resolveSharedTheme('form', formMatch[1])
        } else if (pageMatch) {
            theme = await resolveSharedTheme('page', pageMatch[1])
        } else {
            theme = await resolveInstanceTheme()
        }
        if (theme) {
            html.head.push(buildStyle(theme))
        }
    })
})
