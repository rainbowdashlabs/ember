/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createScopedCrudResource} from './crud'

export const FieldType = {
    DATE: 'DATE',
    ENUM: 'ENUM',
    TEXT: 'TEXT',
    NUMBER: 'NUMBER',
    BOOLEAN: 'BOOLEAN',
} as const
export type FieldTypeName = (typeof FieldType)[keyof typeof FieldType]

export interface EnumOption {
    value: string
    label: string
}

export interface DateFieldConfig {
    kind: 'DATE'
}

export interface EnumFieldConfig {
    kind: 'ENUM'
    options: EnumOption[]
}

export interface TextFieldConfig {
    kind: 'TEXT'
    multiline: boolean
    maxLength: number
}

export interface NumberFieldConfig {
    kind: 'NUMBER'
    min?: number | null
    max?: number | null
    step?: number | null
    unit?: string
}

export interface BooleanFieldConfig {
    kind: 'BOOLEAN'
    trueLabel: string
    falseLabel: string
}

export type FieldConfig =
    | DateFieldConfig
    | EnumFieldConfig
    | TextFieldConfig
    | NumberFieldConfig
    | BooleanFieldConfig

export interface InventoryFieldDefinition {
    id: number
    inventoryId: number
    /** The kind this field describes, or null when it describes the whole inventory. */
    artId?: number | null
    /** The single piece this field describes, or null when it describes more than one. */
    itemId?: number | null
    key: string
    label: string
    fieldType: FieldTypeName
    required: boolean
    sortOrder: number
    config: FieldConfig
}

export interface FieldDefinitionRequest {
    /** The kind this field describes, or null for the whole inventory. At most one of the two. */
    artId?: number | null
    /** The single piece this field describes, or null. At most one of the two. */
    itemId?: number | null
    key: string
    label: string
    fieldType: FieldTypeName
    required: boolean
    sortOrder: number
    config: FieldConfig
}

export interface FieldUpdateRequest {
    label: string
    required: boolean
    sortOrder: number
    config: FieldConfig
}

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

export function defaultFieldConfig(type: FieldTypeName): FieldConfig {
    switch (type) {
        case FieldType.DATE:
            return {kind: 'DATE'}
        case FieldType.ENUM:
            return {kind: 'ENUM', options: []}
        case FieldType.TEXT:
            return {kind: 'TEXT', multiline: false, maxLength: 200}
        case FieldType.NUMBER:
            return {kind: 'NUMBER', min: null, max: null, step: null, unit: ''}
        case FieldType.BOOLEAN:
            return {kind: 'BOOLEAN', trueLabel: 'Yes', falseLabel: 'No'}
    }
}
