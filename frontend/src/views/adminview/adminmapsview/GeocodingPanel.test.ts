/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import GeocodingPanel from './GeocodingPanel.vue'
import {GeocodingProvider, type MapsGeocodingConfig} from '@/api/generated/schema'

/** The address search setting offers nobody or Photon, and a server address only for Photon. */
describe('GeocodingPanel', () => {
    async function panel(config: MapsGeocodingConfig) {
        return mountSuspended(GeocodingPanel, {props: {modelValue: config, defaultServer: 'https://photon.komoot.io'}})
    }

    it('offers only switching it off and Photon', async () => {
        const wrapper = await panel({provider: GeocodingProvider.NONE, baseUrl: null})

        const offered = wrapper.findAll('[data-testid="geocoding-provider"] option').map(o => o.attributes('value'))

        expect(offered).toEqual([GeocodingProvider.NONE, GeocodingProvider.PHOTON])
        expect(wrapper.find('[data-testid="geocoding-server"]').exists()).toBe(false)
    })

    it('takes a server address for Photon', async () => {
        const config: MapsGeocodingConfig = {provider: GeocodingProvider.PHOTON, baseUrl: null}
        const wrapper = await panel(config)

        await wrapper.get('input[data-testid="geocoding-server"]').setValue('https://photon.example')

        expect(config.baseUrl).toBe('https://photon.example')
    })

    it('names the server the instance asks while no address is entered', async () => {
        const wrapper = await panel({provider: GeocodingProvider.PHOTON, baseUrl: null})

        expect(wrapper.get('input[data-testid="geocoding-server"]').attributes('placeholder')).toBe('https://photon.komoot.io')
    })
})
