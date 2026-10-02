/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createScopedCrudResource} from './crud'
import {
    FieldType,
    type FieldConfig,
    type FieldDefinitionRequest,
    type FieldUpdateRequest,
    type InventoryFieldDefinition,
} from './generated/schema'

const fields = createScopedCrudResource<
    InventoryFieldDefinition,
    FieldDefinitionRequest,
    FieldUpdateRequest
>((inventoryId: number) => `/inventories/${inventoryId}/fields`)

export const listFields = fields.list
export const createField = fields.create
export const updateField = fields.update
export const deleteField = fields.remove

/**
 * The fields that describe one piece, with the collision rule already applied by the backend.
 *
 * Where one key is defined for the inventory, for the piece's kind and for the piece itself, the
 * narrowest definition is the one that comes back. A value the piece holds under a key that is not
 * in this list belongs to a kind it no longer has: it stays recorded and stays off the screen.
 */
export async function listItemFields(itemId: number): Promise<InventoryFieldDefinition[]> {
    const res = await client.get<InventoryFieldDefinition[]>(`/inventory-items/${itemId}/fields`)
    return res.data
}

/**
 * The fields that describe a piece, worked out from every definition in the inventory.
 *
 * The backend answers the same question for a piece that already exists; this is for the form that
 * is writing one down for the first time, where there is no id to ask about yet. The rule is the
 * same: where one key is defined at more than one level, the narrowest definition wins.
 */
export function resolveFields(
    defs: InventoryFieldDefinition[],
    artId: number | null,
    itemId: number | null = null,
): InventoryFieldDefinition[] {
    const byKey = new Map<string, {rank: number; def: InventoryFieldDefinition}>()
    for (const def of defs) {
        let rank: number
        if (def.itemId != null) {
            if (itemId == null || def.itemId !== itemId) continue
            rank = 2
        } else if (def.artId != null) {
            if (artId == null || def.artId !== artId) continue
            rank = 1
        } else {
            rank = 0
        }
        const standing = byKey.get(def.key)
        if (!standing || rank >= standing.rank) byKey.set(def.key, {rank, def})
    }
    return [...byKey.values()]
        .map(entry => entry.def)
        .sort((a, b) => a.sortOrder - b.sortOrder || a.key.localeCompare(b.key))
}

export interface NumberFieldViolation {
    limit: 'min' | 'max'
    bound: number
}

export function numberFieldViolation(
    def: InventoryFieldDefinition,
    value: unknown,
): NumberFieldViolation | null {
    if (def.config.kind !== 'NUMBER') return null
    if (value === undefined || value === null || value === '') return null
    const num = Number(value)
    if (Number.isNaN(num)) return null
    if (def.config.min != null && num < def.config.min) return {limit: 'min', bound: def.config.min}
    if (def.config.max != null && num > def.config.max) return {limit: 'max', bound: def.config.max}
    return null
}

export function hasInvalidFieldValues(
    defs: InventoryFieldDefinition[],
    values: Record<string, unknown>,
): boolean {
    return defs.some(def => numberFieldViolation(def, values[def.key]) !== null)
}

/** The settings a field of an inventory starts with; a type an inventory does not offer starts as text. */
export function defaultFieldConfig(type: FieldType): FieldConfig {
    switch (type) {
        case FieldType.DATE:
            return {kind: 'DATE'}
        case FieldType.CHOICE:
            return {kind: 'CHOICE', options: []}
        case FieldType.NUMBER:
            return {kind: 'NUMBER', min: null, max: null, step: null, unit: ''}
        case FieldType.BOOLEAN:
            return {kind: 'BOOLEAN', trueLabel: 'Yes', falseLabel: 'No'}
        default:
            return {kind: 'TEXT', multiline: false, maxLength: 200}
    }
}
