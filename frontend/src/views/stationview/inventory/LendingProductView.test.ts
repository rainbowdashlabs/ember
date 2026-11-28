/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {ProductDetail} from '@/api/generated/schema'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import AvailabilityCalendar from './lendingproductview/AvailabilityCalendar.vue'
import KindRows from './lendingproductview/KindRows.vue'
import PieceList from './lendingproductview/PieceList.vue'
import PiecePanel from './lendingproductview/PiecePanel.vue'
import LendingProductView from './LendingProductView.vue'

const getProduct = vi.fn()
vi.mock('@/api/lending', () => ({getProduct: (...args: unknown[]) => getProduct(...args)}))

const setDays = vi.fn()
const query = {dateFrom: ref('2030-06-12'), dateTo: ref(''), search: ref(''), station: ref(''), sort: ref('best'), setDays}
vi.mock('./lendingview/useShopQuery', () => ({useShopQuery: () => query}))

const push = vi.fn()
vi.mock('vue-router', () => ({
    useRoute: () => ({params: {stationId: 'b1', inventoryId: '7'}, query: {station: 'own', q: 'funk'}}),
    useRouter: () => ({push}),
}))

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

const DETAIL: ProductDetail = {
    stationId: 'b1',
    stationName: 'FF Nord',
    distanceKm: 4.2,
    product: {
        inventoryId: 7, name: 'Spiele', description: '', pictureKey: null, icon: null, color: null, address: null,
        kinds: [
            {artId: null, name: '', description: '', pictureKey: null, icon: null, color: null, free: 2},
            {artId: 3, name: 'Kartenspiele', description: '', pictureKey: null, icon: null, color: null, free: 4},
        ],
        pieces: [{itemId: 14, name: 'Siedler von Catan', description: '', pictureKey: null, free: true}],
    },
}

async function view() {
    const wrapper = shallowMount(LendingProductView, {global: {plugins: [i18n], renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

/** A product page asks the partner with the chosen days and shows kinds and single pieces apart. */
describe('LendingProductView', () => {
    beforeEach(() => {
        query.dateFrom.value = '2030-06-12'
        query.dateTo.value = ''
        setDays.mockReset()
        getProduct.mockReset().mockResolvedValue(DETAIL)
        push.mockReset()
    })

    it('asks for the product with the chosen days and is named after it', async () => {
        const wrapper = await view()

        expect(getProduct).toHaveBeenCalledWith('b1', 7, {from: '2030-06-12', to: undefined})
        expect(wrapper.findComponent(ViewContent).props('title')).toBe('Spiele')
        expect(wrapper.findComponent(KindRows).props('kinds')).toEqual(DETAIL.product.kinds)
        expect(wrapper.findComponent(PieceList).props('windowed')).toBe(true)
    })

    it('keys the calendar rows as the partner keys its counts', async () => {
        const wrapper = await view()

        expect(wrapper.findComponent(AvailabilityCalendar).props('rows')).toEqual([
            {key: 'copies', label: 'Spiele'},
            {key: '3', label: 'Kartenspiele'},
            {key: 'singles', label: 'lendingCalendar.singles'},
        ])
    })

    it('asks again when the days change and hands a pick to the address', async () => {
        const wrapper = await view()

        query.dateTo.value = '2030-06-14'
        await flushPromises()
        expect(getProduct).toHaveBeenLastCalledWith('b1', 7, {from: '2030-06-12', to: '2030-06-14'})

        wrapper.findComponent(AvailabilityCalendar).vm.$emit('pick', '2030-06-20', '')
        expect(setDays).toHaveBeenCalledWith('2030-06-20', '')
    })

    it('opens a single piece in detail', async () => {
        const wrapper = await view()

        wrapper.findComponent(PieceList).vm.$emit('open', DETAIL.product.pieces[0])
        await wrapper.vm.$nextTick()
        expect(wrapper.findComponent(PiecePanel).props('piece')).toEqual(DETAIL.product.pieces[0])
    })

    it('goes back to the shop with its filters', async () => {
        const wrapper = await view()

        await wrapper.findComponent(SecondaryButton).vm.$emit('click')
        expect(push).toHaveBeenCalledWith({name: 'inventory-lending', query: {station: 'own', q: 'funk'}})
    })
})
