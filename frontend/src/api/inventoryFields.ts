/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

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
}

export interface EnumFieldConfig {
    options: EnumOption[]
}

export interface TextFieldConfig {
    multiline: boolean
    maxLength: number
}

export interface NumberFieldConfig {
    min?: number | null
    max?: number | null
    step?: number | null
    unit?: string
}

export interface BooleanFieldConfig {
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

export async function listFields(inventoryId: number): Promise<InventoryFieldDefinition[]> {
    const res = await client.get<InventoryFieldDefinition[]>(`/inventories/${inventoryId}/fields`)
    return res.data
}

export async function createField(
    inventoryId: number,
    data: FieldDefinitionRequest,
): Promise<InventoryFieldDefinition> {
    const res = await client.post<InventoryFieldDefinition>(`/inventories/${inventoryId}/fields`, data)
    return res.data
}

export async function updateField(
    inventoryId: number,
    fieldId: number,
    data: FieldUpdateRequest,
): Promise<InventoryFieldDefinition> {
    const res = await client.put<InventoryFieldDefinition>(
        `/inventories/${inventoryId}/fields/${fieldId}`,
        data,
    )
    return res.data
}

export async function deleteField(inventoryId: number, fieldId: number): Promise<void> {
    await client.delete(`/inventories/${inventoryId}/fields/${fieldId}`)
}

export function defaultFieldConfig(type: FieldTypeName): FieldConfig {
    switch (type) {
        case FieldType.DATE:
            return {}
        case FieldType.ENUM:
            return {options: []}
        case FieldType.TEXT:
            return {multiline: false, maxLength: 200}
        case FieldType.NUMBER:
            return {min: null, max: null, step: null, unit: ''}
        case FieldType.BOOLEAN:
            return {trueLabel: 'Yes', falseLabel: 'No'}
    }
}
