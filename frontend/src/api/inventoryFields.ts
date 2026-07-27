/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
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
    key: string
    label: string
    fieldType: FieldTypeName
    required: boolean
    sortOrder: number
    config: FieldConfig
}

export interface FieldDefinitionRequest {
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
