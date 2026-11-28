/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import type {InventoryItem} from '@/api/generated/schema'
import {useItemTable, type ItemTableApi} from './useItemTable'

vi.mock('@/api/inventoryFields', () => ({listFields: async () => []}))
vi.mock('@/api/inventoryTags', () => ({
    inventoryItemTags: async () => [{itemId: 2, tags: [{id: 1, name: 'Zeltlager'}]}],
}))

const ITEMS = [
    {id: 1, inventoryId: 22, name: 'Teil A', artId: 7},
    {id: 2, inventoryId: 22, name: 'Teil B', artId: null},
    {id: 3, inventoryId: 22, name: 'Teil C', artId: null},
] as unknown as InventoryItem[]

async function itemTable(): Promise<ItemTableApi> {
    let api: ItemTableApi | null = null
    mount(defineComponent({
        setup() {
            api = useItemTable({
                inventoryId: () => 22,
                items: () => ITEMS,
                sizes: () => [],
                hasSizes: () => false,
                isMixed: () => false,
                sizeLabel: () => '',
                assignedName: () => '',
                artName: item => (item.artId === 7 ? 'Bierbank' : ''),
            })
            return () => null
        },
    }))
    await flushPromises()
    return api as unknown as ItemTableApi
}

/** A drawer of different things is looked through by what its pieces are and what they wear. */
describe('useItemTable search', () => {
    beforeEach(() => localStorage.clear())

    it('finds a piece by the name of its kind', async () => {
        const {table} = await itemTable()
        table.search = 'bierbank'

        expect(table.rows.map(row => row.id)).toEqual([1])
    })

    it('finds a piece by its tag', async () => {
        const {table} = await itemTable()
        table.search = 'zeltlager'

        expect(table.rows.map(row => row.id)).toEqual([2])
    })
})
