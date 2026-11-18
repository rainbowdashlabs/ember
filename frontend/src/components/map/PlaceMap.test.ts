/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import PlaceMap from './PlaceMap.vue'

const leaflet = vi.hoisted(() => ({
    options: [] as Record<string, unknown>[],
    browser: {mobile: false},
}))

vi.mock('@/util/leaflet', () => ({
    loadLeaflet: async () => ({
        Browser: leaflet.browser,
        map: (_element: HTMLElement, options: Record<string, unknown>) => {
            leaflet.options.push(options)
            return {setView: () => undefined, remove: () => undefined}
        },
        marker: () => ({addTo: () => ({setLatLng: () => undefined})}),
    }),
    addTileLayer: () => undefined,
}))
vi.mock('@/composables/useMapsConfig', () => ({
    useMapsConfig: () => ({load: async () => ({urlTemplate: null})}),
}))

async function placeMap() {
    const wrapper = mount(PlaceMap, {
        props: {latitude: 52.1, longitude: 10.6, label: 'Gerätehaus'},
        global: {plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}})]},
    })
    await flushPromises()
    return wrapper
}

/**
 * The place map is for looking at: on a touch device it does not move under one finger, so a swipe over it
 * scrolls the page; with a mouse it can still be dragged.
 *
 * @vitest-environment happy-dom
 */
describe('PlaceMap', () => {
    beforeEach(() => {
        leaflet.options.length = 0
    })

    it('leaves one-finger swipes to the page on a touch device', async () => {
        leaflet.browser.mobile = true
        await placeMap()
        expect(leaflet.options.at(-1)).toMatchObject({dragging: false, scrollWheelZoom: false})
    })

    it('can be dragged with a mouse', async () => {
        leaflet.browser.mobile = false
        await placeMap()
        expect(leaflet.options.at(-1)).toMatchObject({dragging: true})
    })
})
