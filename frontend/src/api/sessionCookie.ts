/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * What the page can know about its session, which is very little on purpose.
 *
 * <p>The session token lives in an HttpOnly cookie the page cannot read. Beside it the server sets a
 * readable cookie holding the token every change has to send back as {@link CSRF_HEADER}; another
 * site can make the browser send the cookies, but it cannot read that one. Its presence is also the
 * only sign the page has that a session exists before the session call answers.
 */

/** The readable cookie the server sets beside the session. */
export const CSRF_COOKIE = 'ember_csrf'

/** The header every change sends the readable cookie's value back in. */
export const CSRF_HEADER = 'X-CSRF-Token'

/** The keys where earlier versions kept the session in local storage. */
const LEGACY_SESSION_KEYS = ['session_token', 'session_expires_at']

/**
 * The token a change has to carry, or null where this browser holds no session. Null on the server
 * too, which has no document to read.
 */
export function csrfToken(): string | null {
    if (typeof document === 'undefined') return null
    for (const part of document.cookie.split(';')) {
        const [name, ...value] = part.trim().split('=')
        if (name === CSRF_COOKIE) return decodeURIComponent(value.join('=')) || null
    }
    return null
}

/** Whether this browser carries a session. Whether the session is still good only the server says. */
export function hasSessionCookie(): boolean {
    return csrfToken() !== null
}

/**
 * Removes the session an earlier version kept in local storage. The server no longer accepts it, so
 * it is only a copy of a token lying around for any script to find.
 */
export function forgetLegacySession(): void {
    if (typeof localStorage === 'undefined') return
    for (const key of LEGACY_SESSION_KEYS) localStorage.removeItem(key)
}
