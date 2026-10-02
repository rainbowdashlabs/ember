/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** One inventory a block covers, either whole or by the pieces named. */
export interface BlockInventory {
  inventoryId: number
  inventoryName: string | null
  allItems: boolean
  items: { id: number; name: string | null; internalId: string | null }[]
}

/** Blocks sharing a date range and a reason, shown and lifted as one. */
export interface GroupedBlock {
  key: string
  blockFrom: string
  blockTo: string
  reason: string
  isFullBlock: boolean
  inventories: BlockInventory[]
  blockIds: number[]
}
