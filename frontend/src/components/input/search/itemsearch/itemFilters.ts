/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {InventoryType, ItemCustody, ItemOwner, type InventoryItem} from '@/api/generated/schema'

/** Which pieces an item picker may offer, as its caller asked for them. */
export interface ItemFilterCriteria {
    inventoryId?: number | null
    excludeAssigned?: boolean
    excludeLost?: boolean
    excludeContainerless?: boolean
    ownerKind?: ItemOwner | null
    ownerClusterId?: string | null
    inventoryType?: InventoryType | null
    excludeSpokenFor?: boolean
    excludeUnavailable?: boolean
    keepItemId?: number | null
}

/**
 * Whether a piece is somewhere it can be handed out from: on the station's shelf, at the station for its
 * owner, or with a member. A piece with a partner, in the post, missing, or back in the store of an owner
 * that is not the station is out of reach, however empty its row looks.
 */
export function isWithinReach(item: InventoryItem): boolean {
    switch (item.custody) {
        case ItemCustody.AT_STATION:
        case ItemCustody.WITH_MEMBER:
            return !item.lostAt
        case ItemCustody.WITH_OWNER:
            return !item.lostAt && (item.ownerKind ?? ItemOwner.STATION) === ItemOwner.STATION
        default:
            return false
    }
}

/** What the filters need to know beyond the piece itself. */
export interface ItemFilterContext {
    inventoryTypeOf: (inventoryId: number) => InventoryType | undefined
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
        || type === InventoryType.MIXED
        || criteria.inventoryType === InventoryType.MIXED
}

/**
 * Whether another open movement has promised the piece. The piece the asking movement names itself is
 * spoken for by that movement alone, so it stays on offer to it.
 */
function isSpokenForElsewhere(item: InventoryItem, criteria: ItemFilterCriteria, context: ItemFilterContext): boolean {
    return item.id !== criteria.keepItemId && context.spokenFor.has(item.id)
}

/** Whether the picker may offer the piece at all. */
export function passesItemFilters(item: InventoryItem, criteria: ItemFilterCriteria, context: ItemFilterContext): boolean {
    if (criteria.inventoryId != null && item.inventoryId !== criteria.inventoryId) return false
    if (!onTheSameKindOfShelf(item, criteria, context)) return false
    if (criteria.excludeAssigned && item.assignedTo != null) return false
    if (criteria.excludeLost && item.lostAt) return false
    if (criteria.excludeContainerless && item.containerId == null && item.assignedTo == null) return false
    if (criteria.excludeSpokenFor && isSpokenForElsewhere(item, criteria, context)) return false
    if (criteria.excludeUnavailable && !isWithinReach(item)) return false
    return ownedByTheSameParty(item, criteria)
}
