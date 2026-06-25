/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { InventoryItem } from '@/api/types'

/**
 * Shared emit shape for inventory item action events.
 *
 * Used by components that surface the per-item action set (assign, unassign,
 * edit, mark lost / found, history, delete). Components that add further
 * events intersect this type with their additional events.
 */
export type InventoryItemActionEmits = {
  assign: [item: InventoryItem]
  unassign: [item: InventoryItem]
  edit: [item: InventoryItem]
  markLost: [item: InventoryItem]
  markFound: [item: InventoryItem]
  history: [item: InventoryItem]
  delete: [item: InventoryItem]
}
