/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {InventoryContainer} from '@/api/inventoryContainers'

/**
 * Builds the human-readable location path ("Room / Cabinet / Drawer") for the
 * container with the given id by walking its parent chain in the supplied
 * lookup map. Returns an empty string when the id is missing or unknown;
 * cycles are cut off instead of looping.
 */
export function containerPathFor(
    containerById: Map<number, InventoryContainer>,
    containerId: number | null | undefined,
): string {
    if (containerId == null) return ''
    const segments: string[] = []
    let cursor: number | null | undefined = containerId
    const seen = new Set<number>()
    while (cursor != null && !seen.has(cursor)) {
        seen.add(cursor)
        const node = containerById.get(cursor)
        if (!node) break
        segments.unshift(node.name)
        cursor = node.parentId ?? null
    }
    return segments.join(' / ')
}
