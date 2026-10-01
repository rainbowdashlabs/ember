/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import {InventoryTypes, ItemCustody, ItemOwner} from '@/api/inventory'
import {MovementState, StepActor} from '@/api/movements'
import type {InventoryItem, MovementStanding, RequiredInventoryItem} from '@/api/generated/schema'
import RapidCheckMode from './RapidCheckMode.vue'

const jackets: RequiredInventoryItem = {
    inventoryId: 1, inventoryName: 'Jacken', assignedQuantity: 1, requiredQuantity: 1, inExchangeQuantity: 0,
    hasSizes: false, homogeneous: true, inventoryType: InventoryTypes.INTERNAL, sizes: [],
}

const jacket: InventoryItem = {
    id: 5, inventoryId: 1, name: 'Jacke', internalId: 'INV-5', sizeId: null, artId: null, assignedTo: 1, containerId: null,
    custody: ItemCustody.WITH_MEMBER, custodyMovementId: null, custodyPartnerStationId: null, custodyStationId: null,
    loanRequestItemId: null, lostAt: null, lostNote: null, lostNoteBy: null, metadata: {fields: {}},
    ownerClusterId: null, ownerKind: ItemOwner.STATION, ownerStationId: null,
}

function walkOver(running: MovementStanding | null) {
    return mount(RapidCheckMode, {
        props: {
            uncheckedEntries: [{type: 'item', item: jacket, req: jackets, position: 1, total: 1}],
            availableForInventory: () => [],
            itemLabel: (item: InventoryItem) => item.name,
            sizeLabel: () => '',
            itemNotes: new Map<number, string>(),
            movementOf: () => running,
        },
    })
}

describe('RapidCheckMode', () => {
    it('says where a running exchange stands in the words of the movement list and offers no second one', () => {
        const wrapper = walkOver({
            id: 9, state: MovementState.OPEN, reachedStepLabel: 'Tausch angefordert', currentStepActor: StepActor.STATION,
            ownerKind: ItemOwner.STATION, ownerName: null,
        })

        expect(wrapper.find('[data-testid="movement-step"]').text()).toBe('Tausch angefordert')
        expect(wrapper.find('[data-testid="movement-turn"]').text()).toBe('Wartet auf: Wache')
        expect(wrapper.find('[data-testid="rapid-exchange"]').exists()).toBe(false)
    })

    it('offers the swap where nothing runs on the piece', () => {
        const wrapper = walkOver(null)

        expect(wrapper.find('[data-testid="rapid-on-the-move"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="rapid-exchange"]').exists()).toBe(true)
    })
})
