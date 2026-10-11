/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it, vi} from 'vitest'
import type {Router} from 'vue-router'
import {goBack} from './goBack'

function fakeRouter() {
    return {back: vi.fn(), push: vi.fn()}
}

describe('goBack', () => {
    afterEach(() => {
        window.history.replaceState(null, '')
    })

    it('returns to the page the reader came from', () => {
        window.history.replaceState({back: '/station/events/upcoming'}, '')
        const router = fakeRouter()

        goBack(router as unknown as Router, {name: 'events-upcoming'})

        expect(router.back).toHaveBeenCalledOnce()
        expect(router.push).not.toHaveBeenCalled()
    })

    it('opens the fallback where the page was the first in the tab', () => {
        window.history.replaceState({back: null}, '')
        const router = fakeRouter()

        goBack(router as unknown as Router, {name: 'events-upcoming'})

        expect(router.push).toHaveBeenCalledWith({name: 'events-upcoming'})
        expect(router.back).not.toHaveBeenCalled()
    })
})
