/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {isPublicRoute} from './publicRoute'

/**
 * The one answer to whether a path needs a session, shared by the route guard and the request client.
 *
 * @vitest-environment happy-dom
 */
describe('isPublicRoute', () => {
    it.each([
        '/',
        '/login',
        '/2fa-verify',
        '/pitch',
        '/helpcenter',
        '/helpcenter/station/events',
        '/discovery',
        '/public/station/jugendfeuerwehr-musterstadt',
        '/public/station/jugendfeuerwehr-musterstadt/blog/first-post',
        '/public/waitlist/abc',
        '/f/token123',
        '/s/token123',
        '/forgot-password',
        '/reset-password',
        '/set-password',
        '/confirm-email-change',
        '/apply/verify',
        '/waiting-list/status',
        '/unlock-device',
        '/enroll',
        '/install',
        '/privacy',
        '/terms',
        '/imprint',
        '/patch-notes',
    ])('lets %s through without a session', (path) => {
        expect(isPublicRoute(path)).toBe(true)
    })

    it.each([
        '/station/dashboard/overview',
        '/station',
        '/admin/stations',
        '/cluster',
        '/cluster/members',
        '/account',
        '/account/unlock-device',
        '/cross-station',
        '/reconsent',
        '/station-select',
        '/passkey-offer',
        '/loginx',
        '/sessions',
        '/fields',
        '/publications',
    ])('asks for a session on %s', (path) => {
        expect(isPublicRoute(path)).toBe(false)
    })

    it('trusts a page that declares itself public', () => {
        expect(isPublicRoute('/somewhere/else', {public: true})).toBe(true)
        expect(isPublicRoute('/somewhere/else', {public: false})).toBe(false)
    })
})
