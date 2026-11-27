/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** The operating systems a browser's description is read for. */
export type UserAgentSystem = 'android' | 'ios' | 'windows' | 'mac' | 'linux' | 'unknown'

/** The browsers a browser's description is read for. */
export type UserAgentBrowser = 'edge' | 'opera' | 'firefox' | 'chrome' | 'safari' | 'unknown'

/** How each known system is named; the names are the makers' own and read the same in every language. */
export const SYSTEM_NAMES: Readonly<Record<Exclude<UserAgentSystem, 'unknown'>, string>> = {
    android: 'Android',
    ios: 'iOS',
    windows: 'Windows',
    mac: 'macOS',
    linux: 'Linux',
}

/** How each known browser is named. */
export const BROWSER_NAMES: Readonly<Record<Exclude<UserAgentBrowser, 'unknown'>, string>> = {
    edge: 'Edge',
    opera: 'Opera',
    firefox: 'Firefox',
    chrome: 'Chrome',
    safari: 'Safari',
}

/**
 * The operating system a browser's description names. Phones come first: an iPhone reports "like Mac OS X" and an
 * Android phone reports "Linux", so asking for those first would misplace both.
 */
export function systemOf(agent: string | null): UserAgentSystem {
    if (!agent) return 'unknown'
    if (agent.includes('Android')) return 'android'
    if (/iPhone|iPad|iPod|iOS/.test(agent)) return 'ios'
    if (agent.includes('Windows')) return 'windows'
    if (agent.includes('Mac OS') || agent.includes('Macintosh')) return 'mac'
    if (agent.includes('Linux')) return 'linux'
    return 'unknown'
}

/**
 * The browser a browser's description names. Edge and Opera describe themselves as Chrome too, and Chrome as
 * Safari, so the more particular ones are asked for first. On iPhones every browser reports Safari and names itself
 * with a mark of its own (CriOS, FxiOS, EdgiOS).
 */
export function browserOf(agent: string | null): UserAgentBrowser {
    if (!agent) return 'unknown'
    if (/Edg\/|Edge|EdgiOS/.test(agent)) return 'edge'
    if (agent.includes('OPR') || agent.includes('Opera')) return 'opera'
    if (/Firefox|FxiOS/.test(agent)) return 'firefox'
    if (/Chrome|CriOS/.test(agent)) return 'chrome'
    if (agent.includes('Safari')) return 'safari'
    return 'unknown'
}
