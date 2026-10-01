/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldType, type FieldTypeName} from '@/api/inventoryFields'
import type {FieldValue, InventoryFieldDefinition, InventoryItemMetadata} from '@/api/generated/schema'

/**
 * The metadata attached to an inventory item, or an empty one where the item carries none, so
 * callers can bind directly to the result.
 */
export function parseItemMetadata(raw: InventoryItemMetadata | null | undefined): InventoryItemMetadata {
    return {fields: raw?.fields ?? {}}
}

/**
 * One editor value as the field value of its type, or null where there is nothing to keep.
 *
 * <p>The editors hand back what their inputs hold, which for a number can still be the text that
 * was typed. The value is turned into what the field's type stores, so the payload says what the
 * backend reads rather than whatever the input happened to produce.
 */
export function fieldValueOf(type: FieldTypeName, value: unknown): FieldValue | null {
    if (value === undefined || value === null || value === '') return null
    switch (type) {
        case FieldType.NUMBER: {
            const num = Number(value)
            return Number.isNaN(num) ? null : {kind: 'NUMBER', value: num}
        }
        case FieldType.BOOLEAN:
            return {kind: 'BOOLEAN', value: value === true || value === 'true'}
        case FieldType.DATE:
            return {kind: 'DATE', value: String(value)}
        case FieldType.ENUM:
            return {kind: 'ENUM', value: String(value)}
        case FieldType.TEXT:
            return {kind: 'TEXT', value: String(value)}
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
): InventoryItemMetadata {
    const fields: Record<string, FieldValue> = {}
    for (const def of defs) {
        const value = fieldValueOf(def.fieldType, values[def.key])
        if (value) fields[def.key] = value
    }
    return {fields}
}
