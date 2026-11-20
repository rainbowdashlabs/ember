/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import ItemActionsPanel from './ItemActionsPanel.vue'
import type {InventoryItem} from '@/api/generated/schema'

function panel(onMovement: boolean, over: Partial<InventoryItem> = {}, heldByLoan = false) {
    const item = {id: 5, name: 'Helm', assignedTo: 3, lostAt: null, custody: 'WITH_MEMBER', ...over} as InventoryItem
    return mount(ItemActionsPanel, {props: {item, onMovement, heldByLoan}})
}

describe('ItemActionsPanel', () => {
    it('offers hand-out, take-back and loss for a piece nothing carries', () => {
        expect(panel(false).findAll('button')).toHaveLength(3)
    })

    /** Each of them is refused for a piece an open movement carries, so none is offered. */
    it('offers none of them for a piece a movement carries', () => {
        expect(panel(true).findAll('button')).toHaveLength(0)
    })

    /** A missing piece is neither handed out nor taken back; it comes back by being found. */
    it('offers only finding it again for a missing piece', () => {
        const wrapper = panel(false, {custody: 'LOST', lostAt: '2026-05-01T08:00:00Z'})

        expect(wrapper.findAll('button'), 'found again, and nothing else').toHaveLength(1)
        expect(wrapper.find('[data-testid="item-missing-hint"]').exists()).toBe(true)
    })

    /** A loan holds its piece from approval until its end date, and the station is refused for it. */
    it('offers nothing for a piece a loan holds', () => {
        const wrapper = panel(false, {assignedTo: null, custody: 'WITH_OWNER'}, true)

        expect(wrapper.findAll('button')).toHaveLength(0)
        expect(wrapper.find('[data-testid="item-spoken-for-hint"]').exists()).toBe(true)
    })

    it('offers no hand-out for a piece that is away', () => {
        const wrapper = panel(false, {assignedTo: null, custody: 'WITH_PARTNER'})

        expect(wrapper.findAll('button'), 'only reporting it missing').toHaveLength(1)
    })

    /** The association's piece back in the association's store is not the station's to hand out. */
    it('offers no hand-out for the association\'s piece in the association\'s store', () => {
        const wrapper = panel(false, {assignedTo: null, custody: 'WITH_OWNER', ownerKind: 'CLUSTER'})

        expect(wrapper.findAll('button'), 'only reporting it missing').toHaveLength(1)
    })
})
