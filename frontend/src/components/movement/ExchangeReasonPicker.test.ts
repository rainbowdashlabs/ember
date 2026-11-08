/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import deDE from '@/i18n/de-DE'
import type {InventorySize} from '@/api/generated/schema'
import ExchangeReasonPicker from './ExchangeReasonPicker.vue'

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})

const SIZES = ['S', 'M', 'L'].map((label, position) => ({id: position + 1, inventoryId: 1, label, position}) as InventorySize)

function mountPicker(currentSizeId: number) {
    const state = {newSizeId: currentSizeId as number | null, text: ''}
    const wrapper = mount(ExchangeReasonPicker, {
        props: {
            sizes: SIZES,
            currentSizeId,
            newSizeId: state.newSizeId,
            text: state.text,
            'onUpdate:newSizeId': (value: number | null) => {
                state.newSizeId = value
                void wrapper.setProps({newSizeId: value})
            },
            'onUpdate:text': (value: string) => {
                state.text = value
                void wrapper.setProps({text: value})
            },
        },
        global: {plugins: [i18n]},
    })
    return {wrapper, state}
}

/** Every swap asks why the same way, and the reason moves the wanted size next to the current one. */
describe('ExchangeReasonPicker', () => {
    it('shows the current size and gives no reason until one is chosen', () => {
        const {wrapper, state} = mountPicker(2)

        expect(wrapper.find('[data-testid="exchange-current-size"]').text()).toBe('M')
        expect(state.text).toBe('')
    })

    it('asks one size up for too small and says it is larger', async () => {
        const {wrapper, state} = mountPicker(2)

        await wrapper.find('[data-testid="exchange-reason-too-small"]').trigger('click')

        expect(state.newSizeId).toBe(3)
        expect(state.text).toBe('Zu klein')
        expect(wrapper.find('[data-testid="exchange-relation"]').text()).toContain('Größer')
    })

    it('keeps the largest size for too small and says there is no larger one', async () => {
        const {wrapper, state} = mountPicker(3)

        await wrapper.find('[data-testid="exchange-reason-too-small"]').trigger('click')

        expect(state.newSizeId).toBe(3)
        expect(wrapper.find('[data-testid="exchange-off-the-end"]').exists()).toBe(true)
    })

    it('adds a note to a preset reason and needs words for another reason', async () => {
        const {wrapper, state} = mountPicker(2)

        await wrapper.find('[data-testid="exchange-reason-broken"]').trigger('click')
        await wrapper.find('[data-testid="exchange-note"]').setValue('Naht offen')
        expect(state.text).toBe('Kaputt - Naht offen')

        await wrapper.find('[data-testid="exchange-reason-other"]').trigger('click')
        await wrapper.find('[data-testid="exchange-note"]').setValue('')
        expect(state.text).toBe('')
    })
})
