/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {systemOf} from '@/util/userAgent'

/** Which way a device hands a destination to a navigation app. */
export type NavigationPlatform = 'android' | 'ios' | 'desktop'

/** One way to get to a place: the app it opens and the address that opens it. */
export interface NavigationLink {
    kind: 'navigate' | 'apple' | 'google'
    href: string
}

/** The spot a link leads to, and the name it is shown under in the app. */
export interface NavigationTarget {
    latitude: number
    longitude: number
    label: string
}

/**
 * The platform of the reader's device, as far as navigation is concerned.
 *
 * <p>An iPad presenting itself as a Mac is an iPad here, distinguished by its touch points. Rendered on the
 * server, where there is no device, every reader is a desktop.
 */
export function navigationPlatform(): NavigationPlatform {
    if (typeof navigator === 'undefined') return 'desktop'
    const agent = navigator.userAgent
    const system = systemOf(agent)
    if (system === 'android') return 'android'
    if (system === 'ios' || (agent.includes('Macintosh') && navigator.maxTouchPoints > 1)) return 'ios'
    return 'desktop'
}

/**
 * A place's name as the label of a geo link, which ends at the first closing parenthesis: the
 * parentheses a name carries itself are encoded too, so the whole name reaches the app.
 */
function geoLabel(label: string): string {
    return encodeURIComponent(label).replace(/\(/g, '%28').replace(/\)/g, '%29')
}

/**
 * The links that start navigation to a place on the given platform.
 *
 * <p>Android has a chooser of its own: a single `geo:` link lets the reader pick whichever navigation
 * app they use. iOS has none and would always open Apple Maps for such a link, so an iPhone and a
 * desktop both get Apple Maps and Google Maps side by side; Google Maps opens its app where it is
 * installed.
 */
export function navigationLinks(target: NavigationTarget, platform: NavigationPlatform): NavigationLink[] {
    const point = `${target.latitude},${target.longitude}`
    if (platform === 'android') {
        return [{kind: 'navigate', href: `geo:${point}?q=${point}(${geoLabel(target.label)})`}]
    }
    return [
        {kind: 'apple', href: `https://maps.apple.com/?daddr=${point}&q=${encodeURIComponent(target.label)}`},
        {kind: 'google', href: `https://www.google.com/maps/dir/?api=1&destination=${point}`},
    ]
}
