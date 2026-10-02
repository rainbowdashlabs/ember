/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it} from 'vitest'
import {csrfToken, forgetLegacySession, hasSessionCookie} from './sessionCookie'

/**
 * The page sees its session only through the readable cookie, and forgets the one earlier versions
 * kept in local storage.
 *
 * @vitest-environment happy-dom
 */
describe('the session the page can see', () => {
    afterEach(() => {
        document.cookie = 'ember_csrf=; max-age=0'
        document.cookie = 'other=; max-age=0'
        localStorage.clear()
    })

    it('finds the token among other cookies', () => {
        document.cookie = 'other=1'
        document.cookie = 'ember_csrf=abc%3D'

        expect(csrfToken()).toBe('abc=')
        expect(hasSessionCookie()).toBe(true)
    })

    it('knows of no session without the cookie, or with an emptied one', () => {
        expect(hasSessionCookie()).toBe(false)

        document.cookie = 'ember_csrf='
        expect(csrfToken()).toBeNull()
    })

    it('drops the session an earlier version left in local storage and nothing else', () => {
        localStorage.setItem('session_token', 'old')
        localStorage.setItem('session_expires_at', '2026-01-01T00:00:00Z')
        localStorage.setItem('station_id', 'kept')

        forgetLegacySession()

        expect(localStorage.getItem('session_token')).toBeNull()
        expect(localStorage.getItem('session_expires_at')).toBeNull()
        expect(localStorage.getItem('station_id')).toBe('kept')
    })
})
