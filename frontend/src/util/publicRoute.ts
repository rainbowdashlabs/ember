/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/** Addresses a reader without a session may open, matched as a whole path. */
export const PUBLIC_EXACT_PATHS: readonly string[] = ['/', '/login', '/2fa-verify', '/pitch']

/**
 * Areas a reader without a session may open, matched as the path itself and everything below it.
 *
 * <p>A prefix reaches whole segments only, so `/s` covers `/s/abc` and never `/station`.
 */
export const PUBLIC_PATH_PREFIXES: readonly string[] = [
    '/helpcenter', '/forgot-password', '/set-password', '/set-address', '/reset-password',
    '/confirm-email-change', '/install', '/apply', '/waitlist', '/style', '/privacy', '/terms', '/imprint',
    '/patch-notes', '/discovery', '/public', '/waiting-list', '/unlock-device', '/enroll', '/f', '/s',
]

/**
 * Whether a path opens without a session.
 *
 * <p>The one answer the route guard and the request client share: the guard lets such a path through
 * without a token, and the client leaves a reader on it when a stale token is refused rather than
 * sending them to the login. A page may also declare itself public through `meta.public`, which only
 * the guard can see.
 *
 * @param path the path, without query or hash
 * @param meta the route meta, where the caller has it; only its `public` flag is read
 */
export function isPublicRoute(path: string, meta?: Record<string, unknown>): boolean {
    if (meta?.public === true) return true
    if (PUBLIC_EXACT_PATHS.includes(path)) return true
    return PUBLIC_PATH_PREFIXES.some(prefix => path === prefix || path.startsWith(`${prefix}/`))
}
