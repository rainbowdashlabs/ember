/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {CatalogProduct} from '@/api/generated/schema'
import BulkActions from './lendingofferview/BulkActions.vue'
import CatalogCard from './lendingofferview/CatalogCard.vue'
import LendingOfferView from './LendingOfferView.vue'

const getCatalog = vi.fn()
vi.mock('@/api/lending', () => ({getCatalog: (partnerId?: number) => getCatalog(partnerId)}))
vi.mock('@/api/federation', () => ({listPartners: () => Promise.resolve([{partner: {id: 9}, partnerStationName: 'Nord'}])}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({loaded: ref(true), hasPermission: () => true})}))
const query = {filter: ref('all'), viewAs: ref('')}
vi.mock('@/composables/useRouteQueryRef', () => ({useRouteQueryRef: (key: 'filter' | 'viewAs') => query[key]}))

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

function product(inventoryId: number, offered: boolean): CatalogProduct {
    return {inventoryId, name: `P${inventoryId}`, description: '', pictureKey: null, icon: null, color: null, homogeneous: true,
        pieceCount: 1, share: offered ? {grant: 'GRANT', scope: 'ALL_PARTNERS', partnerIds: []} : null, offered, visible: null, kinds: [], pieces: []}
}

async function view() {
    const wrapper = shallowMount(LendingOfferView, {global: {plugins: [i18n], renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

/** The catalog filters by its address, reads through a partner, and gathers rows for bulk changes. */
describe('LendingOfferView', () => {
    beforeEach(() => {
        query.filter.value = 'all'
        query.viewAs.value = ''
        getCatalog.mockReset().mockResolvedValue([product(1, true), product(2, false)])
    })

    it('narrows to what the filter asks for', async () => {
        const wrapper = await view()
        expect(wrapper.findAllComponents(CatalogCard)).toHaveLength(2)

        query.filter.value = 'notOffered'
        await flushPromises()
        expect(wrapper.findAllComponents(CatalogCard).map(card => card.props('product').inventoryId)).toEqual([2])
    })

    it('reads the offer again through the partner chosen', async () => {
        await view()
        query.viewAs.value = '9'
        await flushPromises()
        expect(getCatalog).toHaveBeenLastCalledWith(9)
    })

    it('gathers ticked rows for the bulk actions', async () => {
        const wrapper = await view()
        expect(wrapper.findComponent(BulkActions).exists()).toBe(false)

        wrapper.findAllComponents(CatalogCard)[1]!.vm.$emit('toggle', 'inventory:2', true)
        await flushPromises()
        expect(wrapper.findComponent(BulkActions).props('rows').map((row: {key: string}) => row.key)).toEqual(['inventory:2'])
    })
})
