/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {InventoryType, ItemCustody, ItemOwner, type MyInventoryItem, type RequiredInventoryItem} from '@/api/generated/schema'

/** A piece the member holds with nothing about it out of the ordinary. */
function piece(fields: Pick<MyInventoryItem, 'id' | 'inventoryId' | 'inventoryName' | 'name'> & Partial<MyInventoryItem>): MyInventoryItem {
    return {
        color: null, custody: ItemCustody.WITH_MEMBER, icon: null, internalId: null, inventoryHomogeneous: true,
        lostAt: null, lostNote: null, lostNoteBy: null, movement: null,
        ownerClusterId: null, ownerKind: ItemOwner.STATION, sizeId: null, sizeName: null, ...fields,
    }
}

const helmet = piece({id: 1, inventoryId: 1, inventoryName: 'Helme', name: 'Helm', sizeName: 'M', sizeId: 2, internalId: 'INV-0001'})
const lostHelmet = piece({
    id: 2, inventoryId: 1, inventoryName: 'Helme', name: 'Helm', sizeName: 'S', sizeId: 1, internalId: 'INV-0003',
    custody: ItemCustody.LOST, lostAt: '2026-05-01T08:00:00Z',
})
const jacket = piece({
    id: 3, inventoryId: 2, inventoryName: 'Jacken', name: 'Jacke', sizeName: 'L', sizeId: 3, internalId: 'INV-0015',
    movement: {id: 1, state: 'OPEN', reachedStepLabel: 'Tausch angefordert', currentStepActor: 'STATION', ownerKind: 'STATION', ownerName: null},
})

/** Two helmets, one of them lost, and a jacket that is already being exchanged. */
export const items: MyInventoryItem[] = [helmet, lostHelmet, jacket]

/** Two jackets are required and one is held, so the jacket block shows one gap. */
export const requirements: RequiredInventoryItem[] = [{
    inventoryId: 2, inventoryName: 'Jacken', inventoryType: InventoryType.INTERNAL, requiredQuantity: 2, assignedQuantity: 1,
    inExchangeQuantity: 0, hasSizes: false, homogeneous: true, sizes: [],
}]
