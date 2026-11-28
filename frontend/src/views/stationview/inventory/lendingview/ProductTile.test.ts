/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it} from 'vitest'
import type {ProductSummary} from '@/api/generated/schema'
import AuthImage from '@/components/display/AuthImage.vue'
import GearGlyph from '@/components/inventory/GearGlyph.vue'
import ProductTile from './ProductTile.vue'

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    messages: {
        'de-DE': {
            lendingShop: {
                kindsOne: '1 Art', kinds: '{count} Arten', piecesOne: '1 Einzelstück', pieces: '{count} Einzelstücke',
                free: '{count} frei', matched: 'enthält: {names}',
            },
            lendingDistance: {distanceKm: '{distance} km entfernt', distanceUnknown: 'Entfernung unbekannt'},
        },
        en: {},
    },
})

function summary(overrides: Partial<ProductSummary['product']> = {}, distanceKm: number | null = 4.2): ProductSummary {
    return {
        stationId: 'b0000000-0000-0000-0000-000000000001',
        stationName: 'FF Nord',
        distanceKm,
        product: {
            inventoryId: 7, name: 'Funk', teaser: 'Digitale Handfunkgeräte', pictureKey: null, icon: null, color: null,
            kinds: 3, pieces: 0, free: 14, matched: [], rank: 0, ...overrides,
        },
    }
}

function tile(value: ProductSummary) {
    return mount(ProductTile, {props: {summary: value}, global: {plugins: [i18n]}})
}

/** A tile shows what the product offers, why a search found it, and where it is. */
describe('ProductTile', () => {
    it('counts kinds and single pieces in the right number', () => {
        expect(tile(summary({kinds: 1, pieces: 2})).text()).toContain('1 Art · 2 Einzelstücke · 14 frei')
        expect(tile(summary({kinds: 3, pieces: 0})).text()).toContain('3 Arten · 14 frei')
    })

    it('shows what a search matched instead of the teaser', () => {
        const wrapper = tile(summary({matched: ['Siedler von Catan']}))

        expect(wrapper.get('[data-testid="lending-product-matched"]').text()).toBe('enthält: Siedler von Catan')
        expect(wrapper.text()).not.toContain('Digitale Handfunkgeräte')
    })

    it('shows the partner photo, or the icon where there is none', () => {
        expect(tile(summary({pictureKey: '11111111-1111-1111-1111-111111111111'})).findComponent(AuthImage).exists()).toBe(true)
        const withoutPhoto = tile(summary())
        expect(withoutPhoto.findComponent(AuthImage).exists()).toBe(false)
        expect(withoutPhoto.findComponent(GearGlyph).exists()).toBe(true)
    })

    it('shows the distance, or that it is unknown', () => {
        expect(tile(summary()).text()).toContain('4.2 km entfernt')
        expect(tile(summary({}, null)).text()).toContain('Entfernung unbekannt')
    })
})
