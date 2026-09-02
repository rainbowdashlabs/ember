/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import LineTargetFields from './LineTargetFields.vue'
import {ItemCustody, ItemOwner, type Inventory, type InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'

const DRAWER: Inventory = {id: 1, stationId: 's', name: 'Handfunkgeräte', hasSizes: false, homogeneous: false, borrowed: false}
const SHELF: Inventory = {id: 2, stationId: 's', name: 'Jacken', hasSizes: true, homogeneous: true, borrowed: false}

const BLUE: InventoryArt = {id: 7, inventoryId: 1, name: 'Funkgerät blau', note: '', position: 0, mergeKey: 'funkgerät blau'}

function piece(id: number, patch: Partial<InventoryItem> = {}): InventoryItem {
  return {id, inventoryId: 1, artId: 7, custody: ItemCustody.AT_STATION, ownerKind: ItemOwner.STATION, ...patch}
}

const ITEMS: InventoryItem[] = [
  piece(1),
  piece(2, {custody: ItemCustody.WITH_MEMBER, assignedTo: 3}),
  piece(3, {custody: ItemCustody.LOST}),
  piece(4, {inventoryId: 2, artId: null}),
]

function fields(kind: 'art' | 'inventory', target: string, quantity: number) {
  return mount(LineTargetFields, {
    props: {
      kind,
      itemId: '',
      artId: kind === 'art' ? target : '',
      inventoryId: kind === 'inventory' ? target : '',
      quantity,
      inventories: [DRAWER, SHELF],
      items: ITEMS,
      arts: [BLUE],
    },
  })
}

describe('LineTargetFields', () => {
  it('counts the pieces of the chosen kind that are at hand', () => {
    expect(fields('art', '7', 2).get('[data-testid="line-target-stock"]').text()).toBe('Vorhanden: 2 Stück')
  })

  it('counts the pieces of the chosen inventory that are at hand', () => {
    expect(fields('inventory', '2', 1).get('[data-testid="line-target-stock"]').text()).toBe('Vorhanden: 1 Stück')
  })

  it('says nothing about a count while nothing is chosen', () => {
    expect(fields('art', '', 2).find('[data-testid="line-target-stock"]').exists()).toBe(false)
  })

  it('reports a line asking for more than the kind has, without refusing it', () => {
    const wrapper = fields('art', '7', 5)

    expect(wrapper.find('[data-testid="line-target-short"]').exists()).toBe(true)
    expect(wrapper.props('quantity')).toBe(5)
  })

  it('offers a search rather than a list of every kind', () => {
    expect(fields('art', '', 1).find('select').exists()).toBe(false)
    expect(fields('art', '', 1).find('input[type="search"]').exists()).toBe(true)
  })

  it('writes the chosen kind back to the form', async () => {
    const wrapper = fields('art', '', 1)
    await wrapper.get('input[type="search"]').trigger('focusin')
    await new Promise(resolve => setTimeout(resolve, 0))
    await wrapper.get('[data-testid="line-target-art"] button').trigger('click')

    expect(wrapper.emitted('update:artId')?.at(-1)).toEqual(['7'])
  })

  it('offers only the inventories that hold one thing in many copies', async () => {
    const wrapper = fields('inventory', '', 1)
    await wrapper.get('input[type="search"]').trigger('focusin')
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.text()).toContain('Jacken')
    expect(wrapper.text()).not.toContain('Handfunkgeräte')
  })
})
