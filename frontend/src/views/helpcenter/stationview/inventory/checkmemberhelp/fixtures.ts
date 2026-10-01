/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {InventoryItem, MemberCheckState, RequiredInventoryItem} from '@/api/generated/schema'
import {InventoryTypes, ItemCustody, ItemOwner} from '@/api/inventory'

/** An inventory a member is required to hold one of, in a single size. */
function required(inventoryId: number, inventoryName: string, sizeLabel: string, assignedQuantity: number): RequiredInventoryItem {
    return {
        inventoryId, inventoryName, assignedQuantity, requiredQuantity: 1, inExchangeQuantity: 0,
        hasSizes: true, homogeneous: true, inventoryType: InventoryTypes.INTERNAL,
        sizes: [{id: inventoryId, inventoryId, label: sizeLabel, note: '', position: 0}],
    }
}

/** A piece the member holds with nothing about it out of the ordinary. */
function held(id: number, inventoryId: number, name: string, internalId: string): InventoryItem {
    return {
        id, inventoryId, name, internalId, sizeId: inventoryId, artId: null, assignedTo: 1, containerId: null,
        custody: ItemCustody.WITH_MEMBER, custodyMovementId: null, custodyPartnerStationId: null, custodyStationId: null,
        loanRequestItemId: null, lostAt: null, lostNote: null, lostNoteBy: null, metadata: {fields: {}},
        ownerClusterId: null, ownerKind: ItemOwner.STATION, ownerStationId: null,
    }
}

/** A helmet the checker has confirmed, a jacket marked lost and boots the member still lacks. */
export const state: MemberCheckState = {
    memberName: 'Max Mustermann',
    memberIdentity: {memberUid: '', stationUid: '', name: 'Max Mustermann', nameColor: null, displayTag: null, stationName: null},
    required: [
        required(1, 'Helme', 'M', 1),
        required(2, 'Jacken', 'L', 1),
        required(3, 'Stiefel', '42', 0),
    ],
    assigned: [
        held(1, 1, 'Helm', 'INV-0001'),
        held(2, 2, 'Jacke', 'INV-0015'),
    ],
    unassigned: {},
    onTheMove: {},
    overtookSelfChecks: [],
    lastCheck: null,
}

/** The answers already given for the pieces above, by piece. */
export const results = [
    {itemId: 1, result: 'CONFIRMED'},
    {itemId: 2, result: 'LOST'},
] as const
