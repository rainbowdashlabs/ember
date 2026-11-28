/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {computed, ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {CartLineView, CartView, CheckoutView} from '@/api/generated/schema'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import CartLineRow from './lendingcartview/CartLineRow.vue'
import CheckoutOutcome from './lendingcartview/CheckoutOutcome.vue'
import LendingCartView from './LendingCartView.vue'

const checkCart = vi.fn()
const checkoutCart = vi.fn()
const getCart = vi.fn()
vi.mock('@/api/lendingCart', () => ({
    checkCart: () => checkCart(),
    checkoutCart: () => checkoutCart(),
    getCart: () => getCart(),
}))

const held = ref<CartView | null>(null)
const setDetails = vi.fn()
const query = ref<Record<string, string>>({station: 'own'})
const reload = vi.fn(async () => { held.value = await getCart() })
vi.mock('@/composables/useLendingCart', () => ({
    useLendingCart: () => ({
        cart: held,
        lines: computed(() => held.value?.lines ?? []),
        load: () => Promise.resolve(held.value),
        reload,
        setQuantity: vi.fn(),
        remove: vi.fn(),
        setDetails: (details: unknown) => setDetails(details),
    }),
}))
vi.mock('vue-router', () => ({
    useRoute: () => ({query: query.value}),
    useRouter: () => ({push: vi.fn(), resolve: () => ({href: '/x'})}),
}))

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

function line(id: number, stationId: string): CartLineView {
    return {id, stationId, stationName: stationId, inventoryId: 7, artId: 3, itemId: null, quantity: 1, inventoryName: 'Funk', lineName: '', pictureKey: null}
}

async function page() {
    const wrapper = shallowMount(LendingCartView, {global: {plugins: [i18n], renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

function sendButton(wrapper: Awaited<ReturnType<typeof page>>) {
    return wrapper.findAllComponents(PrimaryButton).find(button => button.attributes('data-testid') === 'lending-cart-send')!
}

function sendable(wrapper: Awaited<ReturnType<typeof page>>): boolean {
    return sendButton(wrapper).attributes('disabled') !== 'true'
}

/** The cart page asks the stations what is free and sends only a cart that is free on a chosen day. */
describe('LendingCartView', () => {
    beforeEach(() => {
        held.value = {dateFrom: '2030-06-12', dateTo: null, occasion: '', note: '', lines: [line(1, 'a'), line(2, 'b')]}
        checkCart.mockReset().mockResolvedValue([
            {lineId: 1, free: 1, state: 'FREE'},
            {lineId: 2, free: 0, state: 'SHORT'},
        ])
        checkoutCart.mockReset()
        getCart.mockReset().mockResolvedValue({dateFrom: null, dateTo: null, occasion: '', note: '', lines: []})
        reload.mockClear()
        setDetails.mockReset().mockResolvedValue({})
        query.value = {station: 'own'}
    })

    it('takes the days chosen in the catalog into a cart without days', async () => {
        held.value = {...held.value!, dateFrom: null, occasion: 'Fest'}
        query.value = {station: 'own', from: '2030-06-12', to: '2030-06-14'}
        await page()

        expect(setDetails).toHaveBeenCalledWith({dateFrom: '2030-06-12', dateTo: '2030-06-14', occasion: 'Fest', note: ''})
    })

    it('keeps the days of a cart that has its own', async () => {
        query.value = {station: 'own', from: '2030-07-01'}
        await page()

        expect(setDetails).not.toHaveBeenCalled()
    })

    it('marks a line by what the stations answered and holds sending back', async () => {
        const wrapper = await page()

        expect(wrapper.findAllComponents(CartLineRow).map(row => row.props('check')?.state)).toEqual(['FREE', 'SHORT'])
        expect(sendable(wrapper)).toBe(false)
    })

    it('sends a cart that is free in full and shows what was sent', async () => {
        checkCart.mockResolvedValue([{lineId: 1, free: 1, state: 'FREE'}, {lineId: 2, free: 1, state: 'FREE'}])
        const outcome: CheckoutView = {sent: [], failed: ['b'], notesMissing: []}
        checkoutCart.mockResolvedValue(outcome)
        const wrapper = await page()

        expect(sendable(wrapper)).toBe(true)
        await sendButton(wrapper).vm.$emit('click')
        await flushPromises()
        expect(checkoutCart).toHaveBeenCalled()
        expect(reload).toHaveBeenCalled()
        expect(wrapper.findComponent(CheckoutOutcome).props('outcome')).toEqual(outcome)
    })

    it('holds a cart without a first day back without asking anybody to send', async () => {
        held.value = {...held.value!, dateFrom: null}
        checkCart.mockResolvedValue([{lineId: 1, free: 1, state: 'FREE'}, {lineId: 2, free: 1, state: 'FREE'}])
        const wrapper = await page()

        expect(wrapper.get('[data-testid="lending-cart-block"]').text()).toBe('lendingCartPage.needsDay')
        expect(sendable(wrapper)).toBe(false)
    })
})
