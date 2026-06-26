/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {FieldConfig, FieldTypeName} from '@/api/inventoryFields'

/**
 * In-flight edit state for a single inventory field definition.
 */
export interface DraftField {
    id?: number
    key: string
    label: string
    fieldType: FieldTypeName
    required: boolean
    sortOrder: number
    config: FieldConfig
}
