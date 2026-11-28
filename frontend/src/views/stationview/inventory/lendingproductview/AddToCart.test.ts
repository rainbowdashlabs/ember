/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {CartLineView, RemoteProduct} from '@/api/generated/schema'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import QuantityStepper from '@/components/input/number/QuantityStepper.vue'
import AddCountToCart from './AddCountToCart.vue'
import AddPieceToCart from './AddPieceToCart.vue'

const add = vi.fn()
const held = ref<CartLineView[]>([])
vi.mock('@/composables/useLendingCart', () => ({
    useLendingCart: () => ({
        add: (line: unknown) => add(line),
        shareOf: () => (held.value.length ? {lines: held.value, quantity: 1} : undefined),
    }),
}))

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {lendingCart: {inCart: '{count} in der Anfrageliste'}}, en: {}}, missingWarn: false, fallbackWarn: false})

const PRODUCT: RemoteProduct = {
    inventoryId: 7, name: 'Funk', description: '', pictureKey: 'inv', icon: null, color: null, address: null,
    kinds: [{artId: 3, name: 'FuG 8b', description: '', pictureKey: null, icon: null, color: null, free: 4}],
    pieces: [{itemId: 14, name: 'Catan', description: '', pictureKey: 'box', free: true}],
}

function cartLine(line: Partial<CartLineView>): CartLineView {
    return {id: 1, stationId: 'b1', stationName: 'Nord', inventoryId: 7, artId: null, itemId: null, quantity: 1, inventoryName: 'Funk', lineName: '', pictureKey: null, ...line}
}

/** Gear goes in the cart from the product page as a count of a row or as one piece. */
describe('add to cart', () => {
    beforeEach(() => {
        add.mockReset().mockResolvedValue({})
        held.value = []
    })

    it('puts the chosen count of a kind in the cart with the photo it was shown with', async () => {
        const wrapper = mount(AddCountToCart, {props: {stationId: 'b1', product: PRODUCT, kind: PRODUCT.kinds[0]!}, global: {plugins: [i18n]}})

        expect(wrapper.findComponent(QuantityStepper).props('max')).toBe(4)
        await wrapper.findComponent(QuantityStepper).vm.$emit('update:modelValue', 2)
        await wrapper.findComponent(PrimaryButton).vm.$emit('click')
        await flushPromises()
        expect(add).toHaveBeenCalledWith({
            lendingStationUid: 'b1', inventoryId: 7, artId: 3, itemId: null, quantity: 2,
            inventoryName: 'Funk', lineName: 'FuG 8b', pictureKey: 'inv',
        })
    })

    it('shows how many of a kind the cart holds, and offers nothing to put in where none is free', () => {
        held.value = [cartLine({artId: 3, quantity: 2}), cartLine({id: 2, artId: 5, quantity: 9})]
        const wrapper = mount(AddCountToCart, {props: {stationId: 'b1', product: PRODUCT, kind: {...PRODUCT.kinds[0]!, free: 0}}, global: {plugins: [i18n]}})

        expect(wrapper.get('[data-testid="lending-kind-in-cart"]').text()).toContain('2')
        const room = mount(AddCountToCart, {props: {stationId: 'b1', product: PRODUCT, kind: PRODUCT.kinds[0]!}, global: {plugins: [i18n]}})
        expect(room.findComponent(QuantityStepper).props('max')).toBe(2)
        expect(wrapper.findComponent(PrimaryButton).attributes('disabled')).toBeDefined()
    })

    it('puts a piece in once and then shows it is in the cart', async () => {
        const wrapper = mount(AddPieceToCart, {props: {stationId: 'b1', product: PRODUCT, piece: PRODUCT.pieces[0]!}, global: {plugins: [i18n]}})

        await wrapper.findComponent(SecondaryButton).vm.$emit('click')
        await flushPromises()
        expect(add).toHaveBeenCalledWith(expect.objectContaining({itemId: 14, artId: null, quantity: 1, lineName: 'Catan', pictureKey: 'box'}))

        held.value = [cartLine({itemId: 14})]
        await wrapper.vm.$nextTick()
        expect(wrapper.find('[data-testid="lending-piece-in-cart"]').exists()).toBe(true)
    })
})
