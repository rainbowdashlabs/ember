/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import type {ProductList, ProductSummary} from '@/api/generated/schema'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import ProductTile from './ProductTile.vue'
import ShopFilters from './ShopFilters.vue'
import ShopSection from './ShopSection.vue'
import type {ShopSort} from './useShopQuery'

const listProducts = vi.fn()
vi.mock('@/api/lending', () => ({listProducts: (options: unknown) => listProducts(options)}))

function freshQuery() {
    return {dateFrom: ref(''), dateTo: ref(''), search: ref(''), station: ref(''), sort: ref<ShopSort>('best')}
}

let query = freshQuery()
vi.mock('./useShopQuery', () => ({useShopQuery: () => query}))

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    messages: {
        'de-DE': {
            lending: {nothingShared: 'shared', nothingFree: 'free', noAvailable: 'none'},
            lendingShop: {noMatch: 'nomatch', stationOffersNothing: 'stationempty', selectedStation: 'Ausgewählte Wache'},
        },
        en: {},
    },
    missingWarn: false,
    fallbackWarn: false,
})

function product(stationId: string, stationName: string, name: string): ProductSummary {
    return {
        stationId, stationName, distanceKm: null,
        product: {inventoryId: name.length, name, teaser: '', pictureKey: null, icon: null, color: null, kinds: 1, pieces: 0, free: 1, matched: [], rank: 0},
    }
}

function answer(products: ProductSummary[], emptyReason: ProductList['emptyReason'] = null): ProductList {
    return {products, emptyReason}
}

async function section() {
    const wrapper = shallowMount(ShopSection, {global: {plugins: [i18n], renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

function names(wrapper: ReturnType<typeof shallowMount>) {
    return wrapper.findAllComponents(ProductTile).map(tile => (tile.props('summary') as ProductSummary).product.name)
}

function emptyMessage(wrapper: ReturnType<typeof shallowMount>) {
    return wrapper.findComponent(AsyncSection).props('emptyMessage')
}

/** The shop asks the partners with the days and the search, and narrows by station and order itself. */
describe('ShopSection', () => {
    beforeEach(() => {
        query = freshQuery()
        query.dateFrom.value = '2030-06-12'
        query.dateTo.value = ''
        query.search.value = ' funk '
        query.station.value = ''
        query.sort.value = 'best'
        listProducts.mockReset().mockResolvedValue(answer([product('b', 'FF Süd', 'Zelt'), product('a', 'FF Nord', 'Bänke')]))
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('asks with the days and the trimmed search and keeps the partners order', async () => {
        const wrapper = await section()

        expect(listProducts).toHaveBeenCalledWith({q: 'funk', from: '2030-06-12', to: undefined})
        expect(names(wrapper)).toEqual(['Zelt', 'Bänke'])
        expect(wrapper.find('[data-testid="lending-shop-today-hint"]').exists()).toBe(false)
    })

    it('narrows to one station and sorts by name on its own', async () => {
        const wrapper = await section()

        query.sort.value = 'name'
        await wrapper.vm.$nextTick()
        expect(names(wrapper)).toEqual(['Bänke', 'Zelt'])

        query.station.value = 'b'
        await wrapper.vm.$nextTick()
        expect(names(wrapper)).toEqual(['Zelt'])
    })

    it('keeps a chosen station in the list when it offers nothing now', async () => {
        query.station.value = 'gone'
        const wrapper = await section()

        const stations = wrapper.findComponent(ShopFilters).props('stations') as {id: string; name: string}[]
        expect(stations).toContainEqual({id: 'gone', name: 'Ausgewählte Wache'})
        expect(emptyMessage(wrapper)).toBe('stationempty')
    })

    it('shows that nothing is shared before blaming the search', async () => {
        listProducts.mockResolvedValue(answer([], 'NOTHING_SHARED'))
        expect(emptyMessage(await section())).toBe('shared')

        listProducts.mockResolvedValue(answer([], 'NOTHING_FREE'))
        expect(emptyMessage(await section())).toBe('nomatch')

        query.search.value = ''
        expect(emptyMessage(await section())).toBe('free')

        listProducts.mockResolvedValue(answer([], null))
        expect(emptyMessage(await section())).toBe('none')
    })

    it('shows the hint about today without days', async () => {
        query.dateFrom.value = ''
        expect((await section()).find('[data-testid="lending-shop-today-hint"]').exists()).toBe(true)
    })

    it('asks once after typing pauses, at once on new days, and not after leaving', async () => {
        vi.useFakeTimers()
        const wrapper = await section()
        listProducts.mockClear()

        query.search.value = 'fu'
        await wrapper.vm.$nextTick()
        query.search.value = 'funk'
        await wrapper.vm.$nextTick()
        vi.advanceTimersByTime(299)
        expect(listProducts).not.toHaveBeenCalled()
        vi.advanceTimersByTime(1)
        expect(listProducts).toHaveBeenCalledTimes(1)

        query.search.value = 'funkg'
        await wrapper.vm.$nextTick()
        query.dateTo.value = '2030-06-14'
        await wrapper.vm.$nextTick()
        expect(listProducts).toHaveBeenCalledTimes(2)
        vi.advanceTimersByTime(500)
        expect(listProducts).toHaveBeenCalledTimes(2)

        query.search.value = 'funkge'
        await wrapper.vm.$nextTick()
        wrapper.unmount()
        vi.advanceTimersByTime(500)
        expect(listProducts).toHaveBeenCalledTimes(2)
    })

    it('keeps the later answer when an earlier one arrives after it', async () => {
        let resolveFirst!: (value: ProductList) => void
        listProducts
            .mockReset()
            .mockImplementationOnce(() => new Promise<ProductList>(resolve => { resolveFirst = resolve }))
            .mockResolvedValueOnce(answer([product('a', 'FF Nord', 'Neu')]))
        const wrapper = shallowMount(ShopSection, {global: {plugins: [i18n], renderStubDefaultSlot: true}})
        await wrapper.vm.$nextTick()

        query.dateTo.value = '2030-06-13'
        await flushPromises()
        resolveFirst(answer([product('a', 'FF Nord', 'Alt')]))
        await flushPromises()

        expect(names(wrapper)).toEqual(['Neu'])
    })
})
