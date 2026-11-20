/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import InventorySection from './InventorySection.vue'
import type {MovementResponse, MyInventoryItem, RequiredInventoryItem} from '@/api/generated/schema'

const RouterLink = {template: '<a :data-to="JSON.stringify(to)"><slot/></a>', props: ['to']}

/**
 * What the member's gear tab shows of a piece a movement is bringing them.
 *
 * <p>A planned hand-out leaves the piece on the shelf until it is handed over, so the member's list
 * said nothing of it and the gap looked open, offering a second hand-out of the same thing.
 */
function arriving(over: Partial<MovementResponse> = {}): MovementResponse {
    return {
        id: 78,
        state: 'OPEN',
        purpose: 'ISSUE',
        memberId: 41,
        inventoryId: 5,
        inventoryName: 'Sporttaschen',
        incomingItemId: 57,
        incomingItemName: 'Sporttasche',
        currentStepLabel: 'Ausgegeben',
        newSizeName: null,
        oldSizeName: null,
        ...over,
    } as MovementResponse
}

function gap(missing: number): RequiredInventoryItem {
    return {
        inventoryId: 5,
        inventoryName: 'Sporttaschen',
        requiredQuantity: missing,
        assignedQuantity: 0,
    } as RequiredInventoryItem
}

function section(
    incoming: MovementResponse[],
    requirements: RequiredInventoryItem[] = [],
    memberInventory: MyInventoryItem[] = [],
) {
    return mount(InventorySection, {
        props: {memberInventory, requirements, incoming, canAssign: true, canManageInventory: true},
        global: {stubs: {'router-link': RouterLink}},
    })
}

describe('InventorySection', () => {
    it('shows a piece on its way in its inventory, leading to the movement', () => {
        const wrapper = section([arriving()])
        const card = wrapper.get('[data-testid="incoming-gear"]')

        expect(card.text()).toContain('Sporttasche')
        expect(card.text()).toContain('Ausgegeben')
        const link = wrapper.get('[data-testid="incoming-gear-link"]')
        expect(JSON.parse(link.attributes('data-to') ?? '{}')).toMatchObject({
            name: 'inventory-movement-detail',
            params: {id: '78'},
        })
    })

    it('names the inventory and the size where no piece is named yet', () => {
        const wrapper = section([arriving({incomingItemId: null, incomingItemName: null, newSizeName: 'L'})])

        const card = wrapper.get('[data-testid="incoming-gear"]').text()
        expect(card).toContain('Sporttaschen')
        expect(card).toContain('L')
    })

    it('fills a gap rather than standing beside it', () => {
        const wrapper = section([arriving()], [gap(2)])

        expect(wrapper.findAll('[data-testid="incoming-gear"]')).toHaveLength(1)
        expect(wrapper.findAll('[data-testid="missing-requirement"]'), 'one of two is still open').toHaveLength(1)
    })

    /**
     * An exchange brings a piece in place of one the member still holds, so the gap stays open until
     * the old piece has left them. The card shows either way.
     */
    it('fills a gap with an exchange only once the old piece has left the member', () => {
        const swap = arriving({purpose: 'EXCHANGE', itemId: 10})
        const old = {id: 10, inventoryId: 5, inventoryName: 'Sporttaschen', name: 'Sporttasche'} as MyInventoryItem
        const requirement = {...gap(2), assignedQuantity: 1}

        const holding = section([swap], [requirement], [old])
        expect(holding.findAll('[data-testid="incoming-gear"]')).toHaveLength(1)
        expect(holding.findAll('[data-testid="missing-requirement"]'), 'the gap stays open').toHaveLength(1)

        const handedIn = section([swap], [gap(1)])
        expect(handedIn.findAll('[data-testid="incoming-gear"]')).toHaveLength(1)
        expect(handedIn.findAll('[data-testid="missing-requirement"]'), 'the swap now fills it').toHaveLength(0)
    })
})
