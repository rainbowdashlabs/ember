/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {defineComponent, h} from 'vue'
import type {InventoryItem} from '@/api/generated/schema'
import InventoryItemTable from './InventoryItemTable.vue'
import {useItemSelection, type ItemSelection} from './useItemSelection'
import {useItemTable, type ItemTableApi} from './useItemTable'

vi.mock('@/api/inventoryFields', () => ({listFields: async () => []}))
vi.mock('@/api/inventoryTags', () => ({inventoryItemTags: async () => []}))

const ITEMS = [
    {id: 1, inventoryId: 22, name: 'Bierbank 1'},
    {id: 2, inventoryId: 22, name: 'Bierbank 2'},
    {id: 3, inventoryId: 22, name: 'Biertisch'},
] as unknown as InventoryItem[]

async function tableWithSelection() {
    let table: ItemTableApi | null = null
    let selection: ItemSelection | null = null
    const wrapper = mount(defineComponent({
        setup() {
            table = useItemTable({
                inventoryId: () => 22,
                items: () => ITEMS,
                sizes: () => [],
                hasSizes: () => false,
                isMixed: () => false,
                sizeLabel: () => '',
                assignedName: () => '',
            })
            selection = useItemSelection()
            selection.setActive(true)
            return () => h(InventoryItemTable, {items: table!, selection: selection!, memberIdentity: () => null})
        },
    }), {global: {stubs: {'router-link': true, ItemActions: true}}})
    await flushPromises()
    return {wrapper, table: table as unknown as ItemTableApi, selection: selection as unknown as ItemSelection}
}

/** The box in the head ticks what the table shows, not what the inventory holds. */
describe('InventoryItemTable selection', () => {
    beforeEach(() => localStorage.clear())

    it('ticks only the rows the search leaves', async () => {
        const {wrapper, table, selection} = await tableWithSelection()
        table.table.search = 'bank'
        await flushPromises()

        await wrapper.find('[data-testid="items-select-all"]').setValue(true)

        expect(selection.selectedIds.value.sort()).toEqual([1, 2])
    })

    it('ticks one row by its own box', async () => {
        const {wrapper, selection} = await tableWithSelection()

        await wrapper.findAll('[data-testid="item-select"]')[2]?.setValue(true)

        expect(selection.selectedIds.value).toEqual([3])
    })
})
