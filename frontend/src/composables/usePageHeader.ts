/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'

/**
 * Reactive header state shared across the app. {@code ViewContent} writes to it
 * from its {@code title} / {@code subtitle} props; the outer layout components
 * ({@code AdminView}, {@code StationView}, {@code HelpcenterView}) read from it
 * and forward the values to their sidebar/header chrome. The browser tab title
 * uses the same source via {@code usePageTitle}.
 *
 * <p>It is held per request rather than in the module, which the public pages need and the rest of
 * the app is no worse for. Rendered on the server, a value in the module is one value for everybody:
 * it outlives the request that set it, so a station's heading could be drawn above the next
 * station's page, and the browser then hydrated a header the page had not asked for. Held this way
 * the value reaches the browser with the page it belongs to, and the first render there matches what
 * the server sent.
 */
function headerState() {
    return {
        title: useState<string>('page-header-title', () => ''),
        subtitle: useState<string>('page-header-subtitle', () => ''),
        owner: useState<number | null>('page-header-owner', () => null),
    }
}

export function usePageHeader() {
    const {title, subtitle} = headerState()
    return {title: readonly(title), subtitle: readonly(subtitle)}
}

let claims = 0

/**
 * Binds the shared page header to a single owning component instance. Writing claims
 * ownership; releasing clears the header only while the caller still owns it. During a
 * route change the incoming page can write its header before the outgoing page unmounts -
 * without the ownership check, the outgoing page's cleanup would wipe the values the new
 * page just set, leaving every page after the first navigation without title and subtitle.
 *
 * <p>The claim is a number rather than a symbol because it travels to the browser with the rest of
 * the state, and only something a payload can carry survives the journey.
 */
export function claimPageHeader() {
    const {title, subtitle, owner} = headerState()
    const id = ++claims

    return {
        set(newTitle: string, newSubtitle: string) {
            owner.value = id
            title.value = newTitle
            subtitle.value = newSubtitle
        },
        release() {
            if (owner.value !== id) return
            owner.value = null
            title.value = ''
            subtitle.value = ''
        },
    }
}
