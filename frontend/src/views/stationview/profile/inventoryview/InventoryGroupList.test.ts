/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import InventoryGroupList from './InventoryGroupList.vue'
import type {MovementResponse, MyInventoryItem} from '@/api/generated/schema'

vi.mock('@/composables/useSession', () => ({useSession: () => ({isModuleEnabled: () => true})}))

const RouterLink = {template: '<a><slot/></a>', props: ['to']}

function jacket(id: number): MyInventoryItem {
    return {id, name: 'Jacke', inventoryId: 1, inventoryName: 'Jacken', custody: 'WITH_MEMBER', lostAt: null, movement: null} as MyInventoryItem
}

function list(group: {heldQuantity: number, items: MyInventoryItem[], incoming?: MovementResponse[]}) {
    return mount(InventoryGroupList, {
        props: {
            grouped: [{inventoryId: 1, inventoryName: 'Jacken', requiredQuantity: 2, ...group}],
            items: group.items,
        },
        global: {stubs: {'router-link': RouterLink, InventoryItemCard: true}},
    })
}

/** The own inventory counts as the station does, so the gap reads the same on both sides. */
describe('InventoryGroupList', () => {
    it('counts what the station counts as held, a piece in a running exchange included', () => {
        const wrapper = list({heldQuantity: 2, items: [jacket(1)]})

        expect(wrapper.text()).toContain('2 / 2')
        expect(wrapper.find('[data-testid="group-gap"]').exists(), 'no gap for a piece away in an exchange').toBe(false)
    })

    it('shows a gap for a missing piece the station does not count', () => {
        const wrapper = list({heldQuantity: 1, items: [jacket(1), {...jacket(2), custody: 'LOST', lostAt: '2026-05-01T00:00:00Z'}]})

        expect(wrapper.find('[data-testid="group-gap"]').exists()).toBe(true)
    })

    it('shows gear on its way, which fills the gap', () => {
        const coming = {id: 9, state: 'OPEN', purpose: 'ISSUE', memberId: 4, inventoryId: 1, inventoryName: 'Jacken',
            incomingItemId: 7, incomingItemName: 'Jacke', currentStepLabel: 'Ausgegeben'} as MovementResponse
        const wrapper = list({heldQuantity: 1, items: [jacket(1)], incoming: [coming]})

        expect(wrapper.find('[data-testid="incoming-gear"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="group-gap"]').exists(), 'the gear on its way fills it').toBe(false)
    })
})
