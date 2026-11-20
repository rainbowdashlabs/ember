/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import ItemActions from './ItemActions.vue'
import type {InventoryItem} from '@/api/generated/schema'

const stubs = {
    ActionsMenu: {template: '<div><slot/></div>'},
    DropdownMenuItem: {template: '<button><slot/></button>'},
}

function menu(onMovement: boolean) {
    const item = {id: 5, name: 'Helm', assignedTo: 3, lostAt: null} as InventoryItem
    return mount(ItemActions, {props: {item, showActions: true, onMovement}, global: {stubs}})
}

/** A piece an open movement carries is the movement's to move, so the station's menu leaves it alone. */
describe('ItemActions', () => {
    it('offers hand-out, take-back, loss and delete for a piece nothing carries', () => {
        expect(menu(false).findAll('button'), 'six entries, history and edit among them').toHaveLength(6)
    })

    it('offers none of them for a piece a movement carries', () => {
        expect(menu(true).findAll('button'), 'only history and edit stay').toHaveLength(2)
    })
})
