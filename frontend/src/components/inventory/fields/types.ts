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
    /** The kind this field describes, or null when it describes the whole inventory. */
    artId: number | null
    /** The single piece this field describes, or null. At most one of the two is set. */
    itemId: number | null
    key: string
    label: string
    fieldType: FieldTypeName
    required: boolean
    sortOrder: number
    config: FieldConfig
}
