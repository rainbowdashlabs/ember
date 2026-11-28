/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import ProcurementOwnerField from './ProcurementOwnerField.vue'
import type {InventoryType, ItemOwner} from '@/api/generated/schema'

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    missingWarn: false,
    fallbackWarn: false,
    messages: {'de-DE': {}, en: {}},
})

function field(inventoryType: InventoryType | null, owner: ItemOwner | '' = '') {
    return mount(ProcurementOwnerField, {
        global: {plugins: [i18n]},
        props: {modelValue: owner, inventoryType},
    })
}

/** Only a mixed inventory leaves open whose piece an order brings; the other types settle it. */
describe('ProcurementOwnerField', () => {
    it('asks whose piece it is in a mixed inventory, with nothing picked in advance', () => {
        const wrapper = field('MIXED')

        const select = wrapper.find('select[data-testid="procurement-owner"]')
        expect(select.exists()).toBe(true)
        expect((select.element as HTMLSelectElement).value).toBe('')
    })

    it('only shows the settled owner for an internal or an external inventory', () => {
        for (const type of ['INTERNAL', 'EXTERNAL'] as const) {
            const wrapper = field(type)

            expect(wrapper.find('[data-testid="procurement-owner"]').exists()).toBe(false)
            expect(wrapper.find('[data-testid="procurement-owner-settled"]').exists()).toBe(true)
        }
    })

    it('shows nothing before an inventory is chosen', () => {
        expect(field(null).text()).toBe('')
    })
})
