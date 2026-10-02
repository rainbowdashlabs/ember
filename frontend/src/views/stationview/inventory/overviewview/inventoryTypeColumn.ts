/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {InventoryType} from '@/api/generated/schema'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'
import {inventoryTypeLabel, type InventoryTypeTranslator} from '@/util/inventoryType'

/** The key the inventory's kind is remembered under in every overview table. */
export const INVENTORY_TYPE_KEY = 'inventoryType'

/** The kinds of inventory in the order the filter offers them, the same as the pickers that create one. */
const INVENTORY_TYPES: readonly InventoryType[] = [InventoryType.INTERNAL, InventoryType.EXTERNAL, InventoryType.MIXED]

/**
 * The column saying what kind of inventory a row comes out of, which the overview's tables share.
 *
 * @param t      the translation function of the screen
 * @param typeOf the kind of the row's inventory
 */
export function inventoryTypeColumn<Row>(
    t: InventoryTypeTranslator,
    typeOf: (row: Row) => string | null | undefined,
): TableColumn<Row> {
    return {
        key: INVENTORY_TYPE_KEY,
        label: t('inventory.overview.colType'),
        type: ColumnTypes.ENUM,
        value: typeOf,
        options: enumOptions(INVENTORY_TYPES,type => inventoryTypeLabel(t, type)),
    }
}
