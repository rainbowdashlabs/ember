/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {FieldTypeName, InventoryFieldDefinition} from '@/api/inventoryFields'
import type {ItemMetadata} from '@/api/inventory'

export interface ParsedItemMetadata {
    fields: Record<string, {kind: FieldTypeName; value: unknown}>
}

/**
 * Parses the metadata attached to an inventory item. Accepts both the structured
 * object the API serves and a raw JSONB string. Returns an empty record when the
 * input is missing or malformed so callers can bind directly to the result.
 */
export function parseItemMetadata(raw: string | ItemMetadata | null | undefined): ParsedItemMetadata {
    if (!raw) return {fields: {}}
    try {
        const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
        return {
            fields: (parsed?.fields ?? {}) as ParsedItemMetadata['fields'],
        }
    } catch {
        return {fields: {}}
    }
}

/**
 * Builds the structured item metadata payload to send to the API. Skips empty /
 * null / undefined values so the stored blob stays small.
 *
 * @param defs   the inventory's field schema; each def's {@code key} and
 *               {@code fieldType} drive the output entry
 * @param values the editor's current per-key values
 */
export function buildItemMetadata(
    defs: InventoryFieldDefinition[],
    values: Record<string, unknown>,
): ParsedItemMetadata {
    const fields: Record<string, {kind: FieldTypeName; value: unknown}> = {}
    for (const def of defs) {
        const v = values[def.key]
        if (v === undefined || v === null || v === '') continue
        fields[def.key] = {kind: def.fieldType, value: v}
    }
    return {fields}
}
