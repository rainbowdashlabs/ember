/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {InventoryItem} from '@/api/types'

export interface BlockEntry {
  inventoryId: number
  inventoryName: string
  allItems: boolean
  items: InventoryItem[]
  selectedItemIds: Set<number>
  loadingItems: boolean
}
