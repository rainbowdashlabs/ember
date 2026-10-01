/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MyInventoryItem} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'
import {ItemCustody, ItemOwner} from '@/api/inventory'
import type {InventoryGroup} from '@/views/stationview/inventory/memberinventoryview/MemberInventoryGroups.vue'

/** The member whose gear the page shows. */
export const member: MemberLike = {id: 1, name: 'Max Mustermann'}

/** A piece the member holds with nothing about it out of the ordinary. */
function piece(fields: Pick<MyInventoryItem, 'id' | 'inventoryId' | 'inventoryName' | 'name'> & Partial<MyInventoryItem>): MyInventoryItem {
    return {
        color: null, custody: ItemCustody.WITH_MEMBER, icon: null, internalId: null, inventoryHomogeneous: true,
        lostAt: null, lostNote: null, lostNoteBy: null, movementId: null, movementStep: null,
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
    movementId: 1, movementStep: 'Tausch angefordert',
})

/** Two helmets, one of them lost, and a jacket that is already being exchanged. */
export const groups: InventoryGroup[] = [
    {inventoryId: 1, inventoryName: 'Helme', items: [helmet, lostHelmet]},
    {inventoryId: 2, inventoryName: 'Jacken', items: [jacket]},
]

export const items: MyInventoryItem[] = [helmet, lostHelmet, jacket]
