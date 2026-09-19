/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { Inventory, InventoryItem } from '@/api/inventory'
import type { StationMember } from '@/api/types'
import { ColumnTypes, type TableColumn } from '@/components/table/tableColumn'
import { memberNameColumn } from '@/views/stationview/members/listview/memberColumns'

export { MEMBER_NAME_KEY as NAME_KEY } from '@/views/stationview/members/listview/memberColumns'

const INVENTORY_KEY_PREFIX = 'inventory-'

/** What the columns of who holds what need beyond the people themselves. */
export interface InventoryMemberColumnSources {
  t: (key: string) => string
  inventories: readonly Inventory[]
  itemsFor: (memberId: number, inventoryId: number) => InventoryItem[]
  label: (item: InventoryItem) => string
}

function inventoryColumnKey(inventoryId: number): string {
  return `${INVENTORY_KEY_PREFIX}${inventoryId}`
}

/** The id of the inventory a column stands for, or null for the name. */
export function inventoryIdOfColumn(key: string): number | null {
  return key.startsWith(INVENTORY_KEY_PREFIX) ? Number(key.slice(INVENTORY_KEY_PREFIX.length)) : null
}

/**
 * The columns of who holds what: the member, and one column per inventory listing the pieces they
 * hold from it. An inventory column sorts and filters by the pieces as the list names them.
 */
export function inventoryMemberColumns(sources: InventoryMemberColumnSources): TableColumn<StationMember>[] {
  const { t, inventories, itemsFor, label } = sources
  return [
    memberNameColumn(t),
    ...inventories.map(inventory => ({
      key: inventoryColumnKey(inventory.id),
      label: inventory.name ?? '',
      type: ColumnTypes.TEXT,
      value: (member: StationMember) => itemsFor(member.id, inventory.id).map(label),
    })),
  ]
}
