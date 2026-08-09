/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ItemLastCheck} from '@/api/inventoryContainers'
import type {InventoryItem} from '@/api/inventory'

/**
 * An item the container is expected to hold, together with the result the
 * checker assigned to it during the current walk and its previous check.
 */
export interface ExpectedRow {
  item: InventoryItem
  result: 'PENDING' | 'CONFIRMED' | 'NOT_IN_POSSESSION' | 'LOST'
  lastCheck?: ItemLastCheck
}

/**
 * An item that was scanned during the walk although the container is not
 * expected to hold it.
 */
export interface ExtraRow {
  item: InventoryItem
}

/**
 * Tally of the walk results across every expected item plus the extras.
 */
export interface WalkCounts {
  confirmed: number
  missing: number
  pending: number
  extra: number
}
