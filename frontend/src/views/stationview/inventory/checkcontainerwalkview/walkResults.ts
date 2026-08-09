/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ContainerCheckItemResult} from '@/api/inventoryContainers'
import type {ExpectedRow, ExtraRow, WalkCounts} from './types'

/**
 * Tallies how many of the expected items are confirmed, missing or still open.
 */
export function countWalkResults(expected: ExpectedRow[], extra: ExtraRow[]): WalkCounts {
  let confirmed = 0
  let missing = 0
  let pending = 0
  for (const row of expected) {
    if (row.result === 'CONFIRMED') confirmed++
    else if (row.result === 'NOT_IN_POSSESSION' || row.result === 'LOST') missing++
    else pending++
  }
  return {confirmed, missing, pending, extra: extra.length}
}

/**
 * Turns the walk results into the payload that completes the check. Items left
 * untouched count as not in possession.
 */
export function toCheckItems(expected: ExpectedRow[], extra: ExtraRow[]): ContainerCheckItemResult[] {
  const items: ContainerCheckItemResult[] = []
  for (const row of expected) {
    items.push({
      itemId: row.item.id,
      inventoryId: row.item.inventoryId,
      result: row.result === 'PENDING' ? 'NOT_IN_POSSESSION' : row.result,
      note: '',
    })
  }
  for (const row of extra) {
    items.push({
      itemId: row.item.id,
      inventoryId: row.item.inventoryId,
      result: 'EXTRA',
      note: '',
    })
  }
  return items
}
