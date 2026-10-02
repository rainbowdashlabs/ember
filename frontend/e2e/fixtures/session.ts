/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Browser, BrowserContext} from '@playwright/test'

/**
 * How a story carries a session, now that the session is a cookie.
 *
 * <p>A sign-in answers with two cookies: the session itself, which no script can read, and a readable
 * one whose value every change has to send back as {@link CSRF_HEADER}. A request context keeps both
 * in its own cookie jar, and a browser context sends them on every request its pages and its
 * `page.request` make. What a story has to add by hand is the header, and only on changes; reads carry
 * it harmlessly.
 */

/** The readable cookie holding the token a change sends back. */
export const CSRF_COOKIE = 'ember_csrf'

/** The header a change made with the session cookie has to carry. */
export const CSRF_HEADER = 'X-CSRF-Token'

/** A cookie as a browser or request context takes it. */
export type SessionCookie = Awaited<ReturnType<BrowserContext['cookies']>>[number]

/** What a sign-in left behind: the cookies to carry and the token a change sends back. */
export interface DemoSession {
    cookies: SessionCookie[]
    csrf: string
}

/**
 * Signs in through the demo login on the given context and reads back what it set.
 *
 * <p>The context keeps the session in its own jar from here on, which is what a story that goes on
 * asking with it wants. A story that needs the session somewhere else, in a browser or a fresh
 * context, takes the cookies from the answer.
 */
export async function demoSignIn(request: APIRequestContext, email: string): Promise<DemoSession> {
    const login = await request.post('/api/v1/demo/login', {data: {email}})
    if (!login.ok()) throw new Error(`Demo login for ${email} answered ${login.status()}`)
    const {cookies} = await request.storageState()
    const csrf = cookies.find(cookie => cookie.name === CSRF_COOKIE)?.value
    if (!csrf) throw new Error(`The demo login for ${email} set no ${CSRF_COOKIE} cookie`)
    return {cookies: cookies.filter(cookie => cookie.name.startsWith('ember_')), csrf}
}

/**
 * The headers a request made with the session sends: the token proving it came from a page of this
 * application, and the station it acts for where there is one.
 */
export function sessionHeaders(session: {csrf: string}, stationId?: string): Record<string, string> {
    return {[CSRF_HEADER]: session.csrf, ...(stationId ? {'X-Station-Id': stationId} : {})}
}

/** The token a browser context's session wants back on changes, or null where it carries none. */
export async function csrfOf(context: BrowserContext): Promise<string | null> {
    return (await context.cookies()).find(cookie => cookie.name === CSRF_COOKIE)?.value ?? null
}

/**
 * A browser context carrying the session, with what the application keeps in the browser after a
 * sign-in already planted: the consent, the station, and the introductory tour marked as seen where
 * the story asks for that.
 */
export async function browserContextWith(
    browser: Browser,
    session: DemoSession,
    stationId?: string,
    tourSeen = false,
): Promise<BrowserContext> {
    const context = await browser.newContext()
    await context.addCookies(session.cookies)
    await context.addInitScript(([station, seen]) => {
        if (station) window.localStorage.setItem('station_id', station)
        window.localStorage.setItem('storage_consent', 'accepted')
        if (seen) window.localStorage.setItem('onboarding_tour_completed', 'true')
    }, [stationId ?? '', tourSeen ? 'yes' : ''])
    return context
}
