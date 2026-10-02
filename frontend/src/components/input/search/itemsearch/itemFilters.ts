/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    InventoryTypes,
    ItemOwner,
    type InventoryTypeName,
    type ItemOwnerName,
} from '@/api/inventory'
import type {InventoryItem} from '@/api/generated/schema'

/** Which pieces an item picker may offer, as its caller asked for them. */
export interface ItemFilterCriteria {
    inventoryId?: number | null
    excludeAssigned?: boolean
    excludeLost?: boolean
    excludeContainerless?: boolean
    ownerKind?: ItemOwnerName | null
    ownerClusterId?: string | null
    inventoryType?: InventoryTypeName | null
    excludeSpokenFor?: boolean
}

/** What the filters need to know beyond the piece itself. */
export interface ItemFilterContext {
    inventoryTypeOf: (inventoryId: number) => InventoryTypeName | undefined
    spokenFor: Set<number>
}

/**
 * Whose gear a piece is, read off the piece rather than off its inventory.
 *
 * <p>A mixed inventory holds both, so the inventory cannot answer for its pieces; each piece says for
 * itself. A piece that says nothing is the station's, which is what an inventory of the station's own
 * gear writes.
 */
function ownedByTheSameParty(item: InventoryItem, criteria: ItemFilterCriteria): boolean {
    if (criteria.ownerKind == null) return true
    const kind = item.ownerKind ?? ItemOwner.STATION
    if (kind !== criteria.ownerKind) return false
    if (criteria.ownerKind !== ItemOwner.CLUSTER) return true
    return criteria.ownerClusterId == null || item.ownerClusterId === criteria.ownerClusterId
}

/**
 * Whether the piece sits on the same sort of shelf as the movement is about.
 *
 * <p>A shelf that holds both sorts fits either way round, and where the wanted sort is not said the
 * shelf is nobody's business.
 */
function onTheSameKindOfShelf(item: InventoryItem, criteria: ItemFilterCriteria, context: ItemFilterContext): boolean {
    if (criteria.inventoryType == null) return true
    const type = context.inventoryTypeOf(item.inventoryId)
    if (type == null) return false
    return type === criteria.inventoryType
        || type === InventoryTypes.MIXED
        || criteria.inventoryType === InventoryTypes.MIXED
}

/** Whether the picker may offer the piece at all. */
export function passesItemFilters(item: InventoryItem, criteria: ItemFilterCriteria, context: ItemFilterContext): boolean {
    if (criteria.inventoryId != null && item.inventoryId !== criteria.inventoryId) return false
    if (!onTheSameKindOfShelf(item, criteria, context)) return false
    if (criteria.excludeAssigned && item.assignedTo != null) return false
    if (criteria.excludeLost && item.lostAt) return false
    if (criteria.excludeContainerless && item.containerId == null && item.assignedTo == null) return false
    if (criteria.excludeSpokenFor && context.spokenFor.has(item.id)) return false
    return ownedByTheSameParty(item, criteria)
}
