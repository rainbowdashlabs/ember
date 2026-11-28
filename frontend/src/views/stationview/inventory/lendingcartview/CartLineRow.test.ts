/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it} from 'vitest'
import type {CartLineView} from '@/api/generated/schema'
import IconButton from '@/components/button/IconButton.vue'
import QuantityStepper from '@/components/input/number/QuantityStepper.vue'
import CartLineRow from './CartLineRow.vue'

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    messages: {'de-DE': {lendingCartPage: {onlyFree: 'Nur {free} von {quantity} frei', gone: 'weg', singlePiece: 'Einzelstück'}}, en: {}},
    missingWarn: false,
    fallbackWarn: false,
})

function row(line: Partial<CartLineView>, check?: {free: number; state: 'FREE' | 'SHORT' | 'GONE' | 'UNREACHABLE'}) {
    const full: CartLineView = {id: 4, stationId: 'b1', stationName: 'Nord', inventoryId: 7, artId: 3, itemId: null, quantity: 3,
        inventoryName: 'Funk', lineName: 'FuG 8b', pictureKey: null, ...line}
    return mount(CartLineRow, {props: {line: full, check: check && {lineId: 4, ...check}}, global: {plugins: [i18n]}})
}

/** One line of the cart with its count, and what is wrong with it after the check. */
describe('CartLineRow', () => {
    it('shows how many are free where fewer are than asked for', () => {
        expect(row({}, {free: 2, state: 'SHORT'}).get('[data-testid="lending-cart-line-problem"]').text()).toBe('Nur 2 von 3 frei')
        expect(row({}, {free: 3, state: 'FREE'}).find('[data-testid="lending-cart-line-problem"]').exists()).toBe(false)
        expect(row({}, {free: 0, state: 'GONE'}).text()).toContain('weg')
    })

    it('changes a count and removes a line, and shows a piece without a count', async () => {
        const counted = row({})
        await counted.findComponent(QuantityStepper).vm.$emit('update:modelValue', 2)
        expect(counted.emitted('quantity')?.[0]).toEqual([2])
        await counted.findAllComponents(IconButton).at(-1)!.vm.$emit('click')
        expect(counted.emitted('remove')).toHaveLength(1)

        const piece = row({artId: null, itemId: 14, quantity: 1, lineName: 'Catan'})
        expect(piece.findComponent(QuantityStepper).exists()).toBe(false)
        expect(piece.text()).toContain('Einzelstück')
    })
})
