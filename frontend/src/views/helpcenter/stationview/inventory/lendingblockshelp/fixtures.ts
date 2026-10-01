/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {GroupedBlock} from '@/views/stationview/inventory/lendingblocksview/types'

/** A block stopping all lending, one for a single inventory, and one holding back a whole inventory and some pieces, sorted by start as the screen sorts them. */
export const blockGroups: GroupedBlock[] = [
  {
    key: 'full',
    blockFrom: '2026-06-20',
    blockTo: '2026-06-22',
    reason: 'Stadtfest 2026',
    isFullBlock: true,
    inventories: [],
    blockIds: [1],
  },
  {
    key: 'inventory',
    blockFrom: '2026-08-05',
    blockTo: '2026-08-07',
    reason: 'Wachenwettbewerb',
    isFullBlock: false,
    inventories: [{inventoryId: 3, inventoryName: 'Stiefel', allItems: true, items: []}],
    blockIds: [5],
  },
  {
    key: 'mixed',
    blockFrom: '2026-09-10',
    blockTo: '2026-09-11',
    reason: 'Jahreshauptübung',
    isFullBlock: false,
    inventories: [
      {inventoryId: 1, inventoryName: 'Helme', allItems: true, items: []},
      {
        inventoryId: 2,
        inventoryName: 'Jacken',
        allItems: false,
        items: [
          {id: 10, name: 'Jacke', internalId: 'INV-0010'},
          {id: 11, name: 'Jacke', internalId: 'INV-0011'},
        ],
      },
    ],
    blockIds: [2, 3, 4],
  },
]
