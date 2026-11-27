/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {navigationLinks, navigationPlatform} from './navigationLinks'

const target = {latitude: 52.1, longitude: 10.6, label: 'Gerätehaus & Hof'}

/**
 * Navigation to a place: Android picks the app itself, everything else is offered Apple Maps and
 * Google Maps, and the name of the place survives being put into an address.
 */
describe('navigationLinks', () => {
    it('gives Android a single geo link with the place named', () => {
        expect(navigationLinks(target, 'android')).toEqual([
            {kind: 'navigate', href: 'geo:52.1,10.6?q=52.1,10.6(Ger%C3%A4tehaus%20%26%20Hof)'},
        ])
    })

    it('keeps the parentheses of a name inside the geo label', () => {
        const [link] = navigationLinks({...target, label: 'Gerätehaus (Nord)'}, 'android')
        expect(link!.href).toBe('geo:52.1,10.6?q=52.1,10.6(Ger%C3%A4tehaus%20%28Nord%29)')
    })

    it('gives iOS and a desk Apple Maps and Google Maps', () => {
        for (const platform of ['ios', 'desktop'] as const) {
            expect(navigationLinks(target, platform)).toEqual([
                {kind: 'apple', href: 'https://maps.apple.com/?daddr=52.1,10.6&q=Ger%C3%A4tehaus%20%26%20Hof'},
                {kind: 'google', href: 'https://www.google.com/maps/dir/?api=1&destination=52.1,10.6'},
            ])
        }
    })
})

/** The platform comes from the browser's description, with an iPad passing as a Mac distinguished by touch. */
describe('navigationPlatform', () => {
    afterEach(() => {
        vi.unstubAllGlobals()
    })

    function device(userAgent: string, maxTouchPoints = 0) {
        vi.stubGlobal('navigator', {userAgent, maxTouchPoints})
    }

    it('knows an Android phone', () => {
        device('Mozilla/5.0 (Linux; Android 14; Pixel 8) Mobile')
        expect(navigationPlatform()).toBe('android')
    })

    it('knows an iPhone', () => {
        device('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)')
        expect(navigationPlatform()).toBe('ios')
    })

    it('knows an iPad that presents itself as a Mac', () => {
        device('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', 5)
        expect(navigationPlatform()).toBe('ios')
    })

    it('takes a Mac without touch for a desk', () => {
        device('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)')
        expect(navigationPlatform()).toBe('desktop')
    })

    it('takes the server, which has no device, for a desk', () => {
        vi.stubGlobal('navigator', undefined)
        expect(navigationPlatform()).toBe('desktop')
    })
})
