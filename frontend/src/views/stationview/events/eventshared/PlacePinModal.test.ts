/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {defineComponent} from 'vue'
import PlacePinModal from './PlacePinModal.vue'

const findPlace = vi.fn()
const geocodePlace = vi.fn()
const savePlace = vi.fn()
const mapsConfig = {geocoding: true}

vi.mock('@/api', () => ({
    places: {
        findPlace: (...args: unknown[]) => findPlace(...args),
        geocodePlace: (...args: unknown[]) => geocodePlace(...args),
        savePlace: (...args: unknown[]) => savePlace(...args),
    },
}))

vi.mock('@/composables/useMapsConfig', () => ({
    useMapsConfig: () => ({load: async () => mapsConfig}),
}))

const PickerStub = defineComponent({
    name: 'LocationPicker',
    props: {latitude: {type: Number, default: null}, longitude: {type: Number, default: null}},
    emits: ['update:latitude', 'update:longitude'],
    template: `<div data-testid="picker">{{ latitude }},{{ longitude }}
      <button data-testid="drag" @click="$emit('update:latitude', 52.5); $emit('update:longitude', 10.9)"/></div>`,
})

const ModalStub = defineComponent({
    name: 'Modal',
    props: {modelValue: Boolean},
    template: '<div v-if="modelValue"><slot/></div>',
})

async function modal() {
    const mounted = await mountSuspended(PlacePinModal, {
        props: {modelValue: true, text: 'Gerätehaus'},
        global: {stubs: {LocationPicker: PickerStub, Modal: ModalStub}},
    })
    await flushPromises()
    return mounted
}

async function save(mounted: Awaited<ReturnType<typeof modal>>) {
    await mounted.get('[data-testid="place-save"]').trigger('click')
    await flushPromises()
}

/**
 * The place modal starts where the station keeps the place, looks a new place up near the station,
 * stores the pin for the place text with how close the lookup was, and stores a dragged pin as placed
 * by hand.
 */
describe('PlacePinModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mapsConfig.geocoding = true
        findPlace.mockResolvedValue(null)
        savePlace.mockResolvedValue({})
    })

    it('opens at the stored pin without looking the place up', async () => {
        findPlace.mockResolvedValue({text: 'Gerätehaus', latitude: 52.1, longitude: 10.6, precision: null, updatedAt: ''})
        const mounted = await modal()

        expect(mounted.get('[data-testid="picker"]').text()).toContain('52.1,10.6')
        expect(geocodePlace).not.toHaveBeenCalled()
    })

    it('looks a new place up and keeps how close the match was', async () => {
        geocodePlace.mockResolvedValue({match: {latitude: 52.2, longitude: 10.7, precision: 'STREET'}})
        const mounted = await modal()

        expect(geocodePlace).toHaveBeenCalledWith('Gerätehaus')
        expect(mounted.get('[data-testid="geocode-hint"]').text()).toContain('Straße')
        await save(mounted)

        expect(savePlace).toHaveBeenCalledWith({text: 'Gerätehaus', latitude: 52.2, longitude: 10.7, precision: 'STREET'})
        expect(mounted.emitted('update:modelValue')?.at(-1)).toEqual([false])
    })

    it('stores a dragged pin as placed by hand', async () => {
        geocodePlace.mockResolvedValue({match: {latitude: 52.2, longitude: 10.7, precision: 'AREA'}})
        const mounted = await modal()

        await mounted.get('[data-testid="drag"]').trigger('click')
        await save(mounted)

        expect(savePlace).toHaveBeenCalledWith({text: 'Gerätehaus', latitude: 52.5, longitude: 10.9, precision: null})
    })

    it('says a place was not found and waits for a pin by hand', async () => {
        geocodePlace.mockResolvedValue({match: null})
        const mounted = await modal()

        expect(mounted.get('[data-testid="geocode-hint"]').text()).toContain('nicht gefunden')
        expect(mounted.get('[data-testid="place-save"]').attributes('disabled')).toBeDefined()
    })

    it('asks nobody where the instance has no address search', async () => {
        mapsConfig.geocoding = false
        await modal()

        expect(geocodePlace).not.toHaveBeenCalled()
    })

    it('keeps how close a stored pin is when it is saved again unmoved', async () => {
        findPlace.mockResolvedValue({text: 'Gerätehaus', latitude: 52.1, longitude: 10.6, precision: 'EXACT', updatedAt: ''})
        const mounted = await modal()

        await save(mounted)

        expect(savePlace).toHaveBeenCalledWith({text: 'Gerätehaus', latitude: 52.1, longitude: 10.6, precision: 'EXACT'})
    })
})
