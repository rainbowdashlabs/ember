/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {shallowMount} from '@vue/test-utils'
import {describe, expect, it, vi} from 'vitest'
import {createI18n} from 'vue-i18n'
import type {Inventory} from '@/api/generated/schema'
import RequestFromOwnerPanel from './RequestFromOwnerPanel.vue'
import MovementWizard from '../movementwizard/MovementWizard.vue'
import ProcurementCreateModal from '../procurementview/ProcurementCreateModal.vue'

vi.mock('@/api', () => ({}))

const i18n = createI18n({legacy: false, locale: 'de-DE', missingWarn: false, fallbackWarn: false, messages: {'de-DE': {}, en: {}}})

const INVENTORIES = [
    {id: 1, name: 'Eigene', inventoryType: 'INTERNAL', homogeneous: true},
    {id: 2, name: 'Vom Träger', inventoryType: 'EXTERNAL', homogeneous: true},
    {id: 3, name: 'Gemischt', inventoryType: 'MIXED', homogeneous: true},
    {id: 4, name: 'Kiste', inventoryType: 'EXTERNAL', homogeneous: false},
] as Inventory[]

function panel(ownerName: string | null) {
    return shallowMount(RequestFromOwnerPanel, {
        props: {ownerName, inventories: INVENTORIES, members: []},
        global: {plugins: [i18n], stubs: {NeutralContainer: {template: '<div><slot/></div>'}}},
    })
}

/**
 * The panel asks an association in Ember through a request, and lets a station under one outside Ember
 * note its request down as an order of the association's piece.
 */
describe('RequestFromOwnerPanel', () => {
    it('sends a request where the association keeps its gear in Ember', () => {
        const wrapper = panel('Kreisfeuerwehrverband')

        expect(wrapper.findComponent(MovementWizard).exists()).toBe(true)
        expect(wrapper.findComponent(ProcurementCreateModal).exists()).toBe(false)
    })

    it('notes a request down as an order of the association\'s piece elsewhere', () => {
        const order = panel(null).findComponent(ProcurementCreateModal)

        expect(order.props('presetOwner')).toBe('CLUSTER')
        expect(order.props('memberOptional')).toBe(true)
        expect((order.props('inventories') as Inventory[]).map(inventory => inventory.id)).toEqual([2, 3])
    })

    it('lets the page refresh only once the noted request\'s dialog is closed', async () => {
        const wrapper = panel(null)
        const order = wrapper.findComponent(ProcurementCreateModal)

        await wrapper.find('[data-testid="request-note-down"]').trigger('click')
        order.vm.$emit('created')
        await wrapper.vm.$nextTick()
        expect(wrapper.emitted('requested')).toBeUndefined()

        order.vm.$emit('update:modelValue', false)
        await wrapper.vm.$nextTick()
        expect(wrapper.emitted('requested')).toHaveLength(1)
    })
})
