/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {listFields, type InventoryFieldDefinition} from '@/api/inventoryFields'
import {inventoryItemTags} from '@/api/inventoryTags'
import {ItemOwner, type InventoryItem, type InventorySize} from '@/api/inventory'
import {ColumnTypes, type CellValue, type TableColumn} from '@/components/table/tableColumn'
import { useDataTable } from '@/composables/useDataTable'
import { parseItemMetadata, type ParsedItemMetadata } from '../detailview/itemMetadata'

export interface ItemTableOptions {
  inventoryId: () => number
  items: () => InventoryItem[]
  sizes: () => InventorySize[]
  hasSizes: () => boolean
  isMixed: () => boolean
  sizeLabel: (item: InventoryItem) => string
  assignedName: (item: InventoryItem) => string
}

export const FIELD_PREFIX = 'field:'

/** What the holder column can be narrowed to: who holds a piece, and whether it is put away. */
const HOLDING = {
  ASSIGNED: 'assigned',
  NOT_ASSIGNED: 'notAssigned',
  STORED: 'stored',
  NOT_STORED: 'notStored',
} as const

/**
 * The pieces of one inventory as a table: its fixed columns, one per custom field, and the tags
 * where anything wears one.
 *
 * <p>The fields say what they hold, so a date field filters by day, a choice by its options and a
 * number by a range, without anybody wiring that per field.
 */
export function useItemTable(options: ItemTableOptions) {
  const { t } = useI18n()

  const fieldDefs = ref<InventoryFieldDefinition[]>([])
  const tagNamesByItem = ref<Map<number, string[]>>(new Map())

  /**
   * The words the things here wear, read again whenever the list of things is.
   *
   * <p>Read once at mount it went stale the moment somebody tagged something: the tag was in the
   * dialogue that wrote it and nowhere on the page behind it, and the column stayed away until the
   * screen was left and come back to.
   */
  async function loadTags() {
    try {
      const worn = await inventoryItemTags(options.inventoryId())
      tagNamesByItem.value = new Map(worn.map(entry => [entry.itemId, entry.tags.map(tag => tag.name)]))
    } catch {
      tagNamesByItem.value = new Map()
    }
  }

  onMounted(async () => {
    try {
      fieldDefs.value = await listFields(options.inventoryId())
    } catch {
      fieldDefs.value = []
    }
    await loadTags()
  })

  watch(() => options.items(), loadTags)

  /** Whether anything here wears a tag at all, which is what makes the column worth a column. */
  const anyTags = computed(() => [...tagNamesByItem.value.values()].some(names => names.length > 0))

  const metadataById = computed(() => {
    const map = new Map<number, ParsedItemMetadata>()
    for (const item of options.items()) map.set(item.id, parseItemMetadata(item.metadata))
    return map
  })

  function itemTagNames(item: InventoryItem): string[] {
    return tagNamesByItem.value.get(item.id) ?? []
  }

  function rawFieldValue(item: InventoryItem, fieldKey: string): CellValue {
    const value = metadataById.value.get(item.id)?.fields[fieldKey]?.value
    if (value === undefined || value === null) return null
    return typeof value === 'object' ? JSON.stringify(value) : value as string | number | boolean
  }

  function fieldColumn(def: InventoryFieldDefinition): TableColumn<InventoryItem> {
    const base = {
      key: FIELD_PREFIX + def.key,
      label: def.label,
      value: (item: InventoryItem) => rawFieldValue(item, def.key),
      defaultVisible: false,
    }
    const config = def.config
    switch (config.kind) {
      case 'DATE': return {...base, type: ColumnTypes.DATE}
      case 'ENUM': return {...base, type: ColumnTypes.ENUM, options: config.options}
      case 'BOOLEAN': return {...base, type: ColumnTypes.BOOLEAN, booleanLabels: {yes: config.trueLabel, no: config.falseLabel}}
      case 'NUMBER': return {
        ...base,
        type: ColumnTypes.NUMBER,
        display: item => {
          const value = rawFieldValue(item, def.key)
          if (value === null || value === undefined || value === '') return ''
          return config.unit ? `${value} ${config.unit}` : String(value)
        },
      }
      default: return {...base, type: ColumnTypes.TEXT}
    }
  }

  function holdingOf(item: InventoryItem): string[] {
    return [
      item.assignedTo ? HOLDING.ASSIGNED : HOLDING.NOT_ASSIGNED,
      item.containerId ? HOLDING.STORED : HOLDING.NOT_STORED,
    ]
  }

  const sizeOptions = computed(() => options.sizes().map(size => ({value: String(size.id), label: size.label ?? ''})))

  const ownerOptions = computed(() => [
    {value: ItemOwner.STATION, label: t('inventory.edit.ownerStation')},
    {value: ItemOwner.CLUSTER, label: t('inventory.edit.ownerCluster')},
    {value: ItemOwner.PARTNER_STATION, label: t('inventory.edit.ownerPartner')},
  ])

  const holdingOptions = computed(() => [
    {value: HOLDING.ASSIGNED, label: t('inventory.edit.filterAssigned')},
    {value: HOLDING.NOT_ASSIGNED, label: t('inventory.edit.filterNotAssigned')},
    {value: HOLDING.STORED, label: t('inventory.edit.filterInStorage')},
    {value: HOLDING.NOT_STORED, label: t('inventory.edit.filterNotInStorage')},
  ])

  const columns = computed<TableColumn<InventoryItem>[]>(() => [
    {key: 'name', label: t('inventory.edit.colName'), type: ColumnTypes.TEXT, value: item => item.name, pinned: true},
    {key: 'internalId', label: t('inventory.edit.colId'), type: ColumnTypes.TEXT, value: item => item.internalId, pinned: true},
    ...(options.hasSizes() ? [{
      key: 'size', label: t('inventory.edit.colSize'), type: ColumnTypes.ENUM,
      value: (item: InventoryItem) => item.sizeId == null ? null : String(item.sizeId), options: sizeOptions.value,
    }] : []),
    ...(options.isMixed() ? [{
      key: 'owner', label: t('inventory.edit.colOwner'), type: ColumnTypes.ENUM,
      value: (item: InventoryItem) => item.ownerKind, options: ownerOptions.value,
    }] : []),
    ...(anyTags.value ? [{
      key: 'tags', label: t('inventory.tag.column'), type: ColumnTypes.TEXT, value: itemTagNames,
    }] : []),
    {
      key: 'assigned', label: t('inventory.edit.colAssigned'), type: ColumnTypes.ENUM,
      value: holdingOf, options: holdingOptions.value,
      display: options.assignedName,
      sortValue: item => options.assignedName(item) || null,
    },
    ...fieldDefs.value.map(fieldColumn),
  ])

  const table = useDataTable<InventoryItem>({
    id: () => `inventory-items:${options.inventoryId()}`,
    rows: options.items,
    columns,
    rowKey: item => item.id,
    searchText: item => `${options.sizeLabel(item)} ${options.assignedName(item)}`,
  })

  table.sortKey = 'name'

  return {table, itemTagNames}
}

export type ItemTableApi = ReturnType<typeof useItemTable>
