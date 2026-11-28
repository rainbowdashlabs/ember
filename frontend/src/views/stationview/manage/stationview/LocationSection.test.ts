/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {defineComponent, ref} from 'vue'
import LocationSection from './LocationSection.vue'

const getStationLocation = vi.fn()
const geocodeStationAddress = vi.fn()
const updateStationLocation = vi.fn()
const setCoordinates = vi.fn()
const clearStationLocation = vi.fn()
const geocodingOn = ref(true)

vi.mock('@/api', () => ({
    stationManage: {
        getStationLocation: (...args: unknown[]) => getStationLocation(...args),
        geocodeStationAddress: (...args: unknown[]) => geocodeStationAddress(...args),
        updateStationLocation: (...args: unknown[]) => updateStationLocation(...args),
        clearStationLocation: (...args: unknown[]) => clearStationLocation(...args),
    },
}))

vi.mock('@/composables/useMapsConfig', () => ({
    useMapsConfig: () => ({
        config: ref({geocoding: geocodingOn.value}),
        load: async () => ({geocoding: geocodingOn.value}),
    }),
}))

const PickerStub = defineComponent({
    name: 'LocationPicker',
    setup(_, {expose}) {
        expose({setCoordinates})
        return {}
    },
    template: '<div data-testid="picker"><slot name="actions"/></div>',
})

/**
 * Finding the typed station address on the map: the button is offered only where the instance has
 * an address search, sends the unsaved fields, moves the pin, shows how close the match is, and
 * never saves.
 */
describe('LocationSection', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        geocodingOn.value = true
        getStationLocation.mockResolvedValue({
            addressLine: 'Hauptstraße 1',
            postalCode: '38100',
            city: 'Braunschweig',
            country: 'DE',
            latitude: null,
            longitude: null,
        })
    })

    async function mounted() {
        const section = await mountSuspended(LocationSection, {
            global: {stubs: {LocationPicker: PickerStub, GeolocateButton: true}},
        })
        await flushPromises()
        return section
    }

    it('moves the pin to the typed address without saving it', async () => {
        geocodeStationAddress.mockResolvedValue({match: {latitude: 52.26, longitude: 10.52, precision: 'EXACT'}})
        const section = await mounted()

        await section.get('[data-testid="find-on-map"]').trigger('click')
        await flushPromises()

        expect(geocodeStationAddress).toHaveBeenCalledWith({
            addressLine: 'Hauptstraße 1',
            postalCode: '38100',
            city: 'Braunschweig',
            country: 'DE',
        })
        expect(setCoordinates).toHaveBeenCalledWith(52.26, 10.52)
        expect(updateStationLocation).not.toHaveBeenCalled()
        expect(section.find('[data-testid="geocode-hint"]').exists()).toBe(false)
    })

    it('asks to drag the pin when only the street was found', async () => {
        geocodeStationAddress.mockResolvedValue({match: {latitude: 52.2, longitude: 10.5, precision: 'STREET'}})
        const section = await mounted()

        await section.get('[data-testid="find-on-map"]').trigger('click')
        await flushPromises()

        expect(section.get('[data-testid="geocode-hint"]').text()).toContain('Straße')
    })

    it('asks to drag the pin onto the station when only the town was found', async () => {
        geocodeStationAddress.mockResolvedValue({match: {latitude: 52.2, longitude: 10.5, precision: 'AREA'}})
        const section = await mounted()

        await section.get('[data-testid="find-on-map"]').trigger('click')
        await flushPromises()

        expect(section.get('[data-testid="geocode-hint"]').text()).toContain('Nur der Ort')
    })

    it('says the address was not found and leaves the pin alone', async () => {
        geocodeStationAddress.mockResolvedValue({match: null})
        const section = await mounted()

        await section.get('[data-testid="find-on-map"]').trigger('click')
        await flushPromises()

        expect(setCoordinates).not.toHaveBeenCalled()
        expect(section.get('[data-testid="geocode-hint"]').text()).toContain('nicht gefunden')
    })

    it('reports a failed lookup instead of moving the pin', async () => {
        geocodeStationAddress.mockRejectedValue(new Error('offline'))
        const section = await mounted()

        await section.get('[data-testid="find-on-map"]').trigger('click')
        await flushPromises()

        expect(setCoordinates).not.toHaveBeenCalled()
        expect(section.emitted('error')).toHaveLength(1)
    })

    it('offers no lookup where the instance has no address search', async () => {
        geocodingOn.value = false
        const section = await mounted()

        expect(section.find('[data-testid="find-on-map"]').exists()).toBe(false)
    })

    it('offers no lookup before any part of the address is typed', async () => {
        getStationLocation.mockResolvedValue({
            addressLine: null, postalCode: null, city: null, country: 'DE', latitude: null, longitude: null,
        })
        const section = await mounted()

        expect(section.get('[data-testid="find-on-map"]').attributes('disabled')).toBeDefined()
    })

    it('drops the hint once the location is cleared', async () => {
        geocodeStationAddress.mockResolvedValue({match: {latitude: 52.2, longitude: 10.5, precision: 'STREET'}})
        clearStationLocation.mockResolvedValue(undefined)
        const section = await mounted()

        await section.get('[data-testid="find-on-map"]').trigger('click')
        await flushPromises()
        expect(section.find('[data-testid="geocode-hint"]').exists()).toBe(true)

        await section.get('[data-testid="location-clear"]').trigger('click')
        await flushPromises()
        expect(clearStationLocation).toHaveBeenCalled()
        expect(section.find('[data-testid="geocode-hint"]').exists()).toBe(false)
    })
})
