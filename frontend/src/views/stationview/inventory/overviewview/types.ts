/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { InventoryItem, MemberIdentity } from '@/api/types'

/**
 * A lost inventory item enriched with the data the overview view needs to
 * render it (its inventory name/type, size label, and owner identity).
 */
export interface LostItem {
    item: InventoryItem
    inventoryName: string
    inventoryType: string
    sizeName: string
    ownerName: string
    ownerIdentity: MemberIdentity | null
}
