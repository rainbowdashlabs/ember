/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {usableRedirect} from './redirect'

describe('usableRedirect', () => {
    it('follows a path on this instance', () => {
        expect(usableRedirect('/station/dashboard/overview')).toBe(true)
        expect(usableRedirect('/station/events/12?tab=list')).toBe(true)
    })

    it('refuses anything that leaves this instance', () => {
        expect(usableRedirect('//evil.example')).toBe(false)
        expect(usableRedirect('https://evil.example')).toBe(false)
        expect(usableRedirect('station/dashboard')).toBe(false)
    })

    /**
     * The two pages that exist to send a visitor onwards cannot be a destination, or they hand the
     * visitor back and forth: the picker sends them to the requirements, which send them to the
     * picker.
     */
    it('refuses the pages that only forward', () => {
        expect(usableRedirect('/cross-station')).toBe(false)
        expect(usableRedirect('/cross-station?redirect=/station/requirements')).toBe(false)
        expect(usableRedirect('/station/requirements')).toBe(false)
        expect(usableRedirect('/station/requirements?redirect=/cross-station')).toBe(false)
    })

    it('refuses nothing at all', () => {
        expect(usableRedirect(null)).toBe(false)
        expect(usableRedirect(undefined)).toBe(false)
        expect(usableRedirect('')).toBe(false)
    })
})
