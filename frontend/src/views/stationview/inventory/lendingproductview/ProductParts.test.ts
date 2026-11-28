/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount, shallowMount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it} from 'vitest'
import type {ProductDetail, RemoteKind} from '@/api/generated/schema'
import LinkButton from '@/components/button/LinkButton.vue'
import ProseExcerpt from '@/components/display/ProseExcerpt.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import AvailabilityCalendar from './AvailabilityCalendar.vue'
import KindRows from './KindRows.vue'
import PiecePanel from './PiecePanel.vue'
import ProductDescription from './ProductDescription.vue'
import ProductHeader from './ProductHeader.vue'

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    messages: {
        'de-DE': {
            lendingShop: {free: '{count} frei'},
            lendingDistance: {distanceUnknown: 'Entfernung unbekannt', distanceKm: '{distance} km'},
            lendingProduct: {more: 'Mehr anzeigen', less: 'Weniger anzeigen', openMap: 'Karte öffnen'},
        },
        en: {},
    },
    missingWarn: false,
    fallbackWarn: false,
})
const global = {plugins: [i18n], renderStubDefaultSlot: true}

function detail(address: ProductDetail['product']['address'], distanceKm: number | null): ProductDetail {
    return {
        stationId: 'b1', stationName: 'FF Nord', distanceKm,
        product: {inventoryId: 7, name: 'Zelte', description: '', pictureKey: null, icon: null, color: null, address, kinds: [], pieces: []},
    }
}

/** The parts of a product page: where the gear is kept, a folded description, the kind rows, one piece. */
describe('product page parts', () => {
    it('shows where the gear is kept with a map search, or no line without an address', () => {
        const header = shallowMount(ProductHeader, {
            props: {detail: detail({addressLine: 'Wachweg 1', postalCode: '12345', city: 'Nord', country: 'DE'}, 2.25)},
            global,
        })
        expect(header.get('[data-testid="lending-product-address"]').text()).toContain('Wachweg 1, 12345 Nord')
        expect(header.findComponent(AppLink).props('href'))
            .toBe('https://www.openstreetmap.org/search?query=Wachweg%201%2C%2012345%20Nord')
        expect(header.text()).toContain('2.3 km')

        const bare = shallowMount(ProductHeader, {props: {detail: detail(null, null)}, global})
        expect(bare.find('[data-testid="lending-product-address"]').exists()).toBe(false)
        expect(bare.text()).toContain('Entfernung unbekannt')
    })

    it('folds a description only where it is cut, opens it on request, and shows nothing for none', async () => {
        const folded = shallowMount(ProductDescription, {props: {text: 'Lang', folded: true}, global})
        expect(folded.findComponent(LinkButton).exists()).toBe(false)

        await folded.findComponent(ProseExcerpt).vm.$emit('measured', true)
        await folded.findComponent(LinkButton).vm.$emit('click')
        expect(folded.findComponent(ProseExcerpt).exists()).toBe(false)
        expect(folded.findComponent(LinkButton).attributes('aria-expanded')).toBe('true')

        expect(shallowMount(ProductDescription, {props: {text: '  '}, global}).html()).toBe('<!--v-if-->')
    })

    it('shows the row of an inventory of copies under the product name and mutes a count of none', () => {
        const kinds: RemoteKind[] = [
            {artId: null, name: '', description: '', pictureKey: null, icon: null, color: null, free: 0},
        ]
        const rows = mount(KindRows, {props: {kinds, stationUid: 'b1', productName: 'Zelte'}, global})

        expect(rows.text()).toContain('Zelte')
        expect(rows.find('.text-success').exists()).toBe(false)
    })

    it('shows one piece with its own calendar and passes a pick on', async () => {
        const panel = shallowMount(PiecePanel, {
            props: {
                modelValue: true, stationUid: 'b1', inventoryId: 7, dateFrom: '', dateTo: '',
                piece: {itemId: 14, name: 'Catan', description: '', pictureKey: null, free: true},
            },
            global,
        })
        const calendar = panel.findComponent(AvailabilityCalendar)

        expect(calendar.props('itemId')).toBe(14)
        expect(calendar.props('rows')).toEqual([{key: 'piece', label: 'Catan'}])
        await calendar.vm.$emit('pick', '2030-06-12', '')
        expect(panel.emitted('pick')?.[0]).toEqual(['2030-06-12', ''])
    })
})
