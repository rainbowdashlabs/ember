/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Inventory, InventoryItem} from '@/api/generated/schema'
import type {BlockEntry} from '@/views/stationview/inventory/lendingblockscreateview/types'

/** The period and reason of the block being written. */
export const blockPeriod = {from: '2026-06-20', to: '2026-06-22', reason: 'Stadtfest 2026'}

/** A free jacket in the station's store, as the scope picker lists it. */
function jacket(id: number, internalId: string): InventoryItem {
  return {
    artId: null,
    assignedTo: null,
    containerId: null,
    custody: 'AT_STATION',
    custodyMovementId: null,
    custodyPartnerStationId: null,
    custodyStationId: null,
    id,
    internalId,
    inventoryId: 2,
    loanRequestItemId: null,
    lostAt: null,
    lostNote: null,
    lostNoteBy: null,
    metadata: {fields: {}},
    name: 'Jacke',
    ownerClusterId: null,
    ownerKind: 'STATION',
    ownerStationId: null,
    sizeId: null,
  }
}

/**
 * One inventory blocked whole and one blocked piece by piece, with two of three jackets chosen.
 * Built per call, since an entry carries the set of chosen pieces.
 */
export function blockEntries(): BlockEntry[] {
  return [
  {
    inventoryId: 1,
    inventoryName: 'Helme',
    allItems: true,
    items: [],
    selectedItemIds: new Set(),
    loadingItems: false,
  },
  {
    inventoryId: 2,
    inventoryName: 'Jacken',
    allItems: false,
    items: [jacket(10, 'INV-0010'), jacket(11, 'INV-0011'), jacket(12, 'INV-0012')],
    selectedItemIds: new Set([10, 11]),
    loadingItems: false,
  },
  ]
}

/** The inventory still left to add. */
export const availableInventories: Inventory[] = [
  {
    borrowed: false,
    color: null,
    hasSizes: true,
    homogeneous: false,
    icon: null,
    id: 3,
    inventoryType: 'INTERNAL',
    name: 'Stiefel',
    stationId: 'hafenstadt',
  },
]
