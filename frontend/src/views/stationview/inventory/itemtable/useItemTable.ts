/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {listFields, type InventoryFieldDefinition} from '@/api/inventoryFields'
import {ItemOwner, type InventoryItem} from '@/api/inventory'
import type { ColumnPickerOption } from '@/components/table/columns'
import { byValue, useSortable } from '@/composables/useSortable'
import { formatDate } from '@/util/format'
import { parseItemMetadata, type ParsedItemMetadata } from '../detailview/itemMetadata'

export interface ItemTableColumn {
  key: string
  label: string
}

export interface ItemTableOptions {
  inventoryId: () => number
  items: () => InventoryItem[]
  hasSizes: () => boolean
  isMixed: () => boolean
  sizeLabel: (item: InventoryItem) => string
  assignedName: (item: InventoryItem) => string
}

const FIELD_PREFIX = 'field:'

export function useItemTable(options: ItemTableOptions) {
  const { t } = useI18n()

  const fieldDefs = ref<InventoryFieldDefinition[]>([])
  const searchText = ref('')
  const hiddenKeys = ref<Set<string>>(new Set())
  const extraKeys = ref<Set<string>>(new Set())
  const columnMultiFilters = ref<Map<string, Set<string>>>(new Map())
  const columnEmptyFilters = ref<Set<string>>(new Set())

  const filterModalOpen = ref(false)
  const filterModalColumn = ref('name')
  const filterModalLabel = ref('')
  const filterModalValues = ref<string[]>([])
  const filterModalSelected = ref<Set<string>>(new Set())
  const filterModalIncludeEmpty = ref(false)

  onMounted(async () => {
    try {
      fieldDefs.value = await listFields(options.inventoryId())
    } catch {
      fieldDefs.value = []
    }
  })

  const defaultColumns = computed<ItemTableColumn[]>(() => [
    { key: 'name', label: t('inventory.edit.colName') },
    { key: 'internalId', label: t('inventory.edit.colId') },
    ...(options.hasSizes() ? [{ key: 'size', label: t('inventory.edit.colSize') }] : []),
    ...(options.isMixed() ? [{ key: 'owner', label: t('inventory.edit.colOwner') }] : []),
    { key: 'assigned', label: t('inventory.edit.colAssigned') },
  ])

  const fieldColumns = computed<ItemTableColumn[]>(() =>
    fieldDefs.value.map(def => ({ key: FIELD_PREFIX + def.key, label: def.label })))

  const visibleColumns = computed<ItemTableColumn[]>(() => [
    ...defaultColumns.value.filter(c => !hiddenKeys.value.has(c.key)),
    ...fieldColumns.value.filter(c => extraKeys.value.has(c.key)),
  ])

  const visibleFieldColumns = computed<ItemTableColumn[]>(() =>
    fieldColumns.value.filter(c => extraKeys.value.has(c.key)))

  const pickerOptions = computed<ColumnPickerOption[]>(() => [
    ...defaultColumns.value
      .filter(c => c.key !== 'name' && c.key !== 'internalId')
      .map(c => ({ key: c.key, label: c.label, visible: !hiddenKeys.value.has(c.key) })),
    ...fieldColumns.value.map(c => ({ key: c.key, label: c.label, visible: extraKeys.value.has(c.key) })),
  ])

  function isColumnVisible(key: string): boolean {
    return visibleColumns.value.some(c => c.key === key)
  }

  function toggleColumn(key: string | number) {
    const columnKey = String(key)
    const target = columnKey.startsWith(FIELD_PREFIX) ? extraKeys : hiddenKeys
    const next = new Set(target.value)
    if (next.has(columnKey)) { next.delete(columnKey) } else { next.add(columnKey) }
    target.value = next
  }

  const metadataById = computed(() => {
    const map = new Map<number, ParsedItemMetadata>()
    for (const item of options.items()) map.set(item.id, parseItemMetadata(item.metadata))
    return map
  })

  function fieldValue(item: InventoryItem, fieldKey: string): string {
    const def = fieldDefs.value.find(d => d.key === fieldKey)
    const entry = metadataById.value.get(item.id)?.fields[fieldKey]
    if (!def || entry === undefined || entry.value === undefined || entry.value === null) return ''
    const value = entry.value
    const config = def.config
    switch (config.kind) {
      case 'DATE': return formatDate(String(value))
      case 'ENUM': return config.options.find(o => o.value === value)?.label ?? String(value)
      case 'BOOLEAN': return value ? config.trueLabel : config.falseLabel
      case 'NUMBER': return config.unit ? `${value} ${config.unit}` : String(value)
      default: return String(value)
    }
  }

  function ownerLabel(item: InventoryItem): string {
    if (item.ownerKind === ItemOwner.STATION) return t('inventory.edit.ownerStation')
    if (item.ownerKind === ItemOwner.CLUSTER) return t('inventory.edit.ownerCluster')
    return ''
  }

  function columnValue(item: InventoryItem, key: string): string {
    switch (key) {
      case 'name': return item.name ?? ''
      case 'internalId': return item.internalId ?? ''
      case 'size': return options.sizeLabel(item)
      case 'owner': return ownerLabel(item)
      case 'assigned': return item.assignedTo ? options.assignedName(item) : ''
      default: return fieldValue(item, key.slice(FIELD_PREFIX.length))
    }
  }

  function assignedStatuses(item: InventoryItem): string[] {
    return [
      item.assignedTo ? t('inventory.edit.filterAssigned') : t('inventory.edit.filterNotAssigned'),
      item.containerId ? t('inventory.edit.filterInStorage') : t('inventory.edit.filterNotInStorage'),
    ]
  }

  function filterValues(item: InventoryItem, key: string): string[] {
    if (key === 'assigned') return assignedStatuses(item)
    const value = columnValue(item, key)
    return value ? [value] : []
  }

  function distinctValues(key: string): string[] {
    if (key === 'assigned') {
      return [
        t('inventory.edit.filterAssigned'),
        t('inventory.edit.filterNotAssigned'),
        t('inventory.edit.filterInStorage'),
        t('inventory.edit.filterNotInStorage'),
      ]
    }
    const values = new Set<string>()
    for (const item of options.items()) {
      const value = columnValue(item, key)
      if (value) values.add(value)
    }
    return [...values].sort()
  }

  const searchedItems = computed(() => {
    const query = searchText.value.trim().toLowerCase()
    if (!query) return options.items()
    return options.items().filter(item =>
      ['name', 'internalId', 'size', 'assigned'].some(key => columnValue(item, key).toLowerCase().includes(query)))
  })

  const matchingItems = computed(() => {
    let list = searchedItems.value
    for (const [key, selectedValues] of columnMultiFilters.value) {
      if (selectedValues.size === 0) continue
      const includeEmpty = columnEmptyFilters.value.has(key)
      list = list.filter(item => {
        const values = filterValues(item, key)
        if (values.length === 0 || values.every(v => !v)) return includeEmpty
        return values.some(v => selectedValues.has(v))
      })
    }
    for (const key of columnEmptyFilters.value) {
      if ((columnMultiFilters.value.get(key)?.size ?? 0) > 0) continue
      list = list.filter(item => {
        const values = filterValues(item, key)
        return values.length === 0 || values.every(v => !v)
      })
    }
    return list
  })

  const {sorted: filteredItems, toggle: toggleSort, icon: sortIcon} = useSortable<InventoryItem, string>({
    items: matchingItems,
    initialKey: 'name',
    comparators: key => byValue(item => columnValue(item, key)),
  })

  function hasActiveFilter(key: string): boolean {
    const multi = columnMultiFilters.value.get(key)
    if (multi && multi.size > 0) return true
    return columnEmptyFilters.value.has(key)
  }

  function openFilterModal(key: string, label: string) {
    filterModalColumn.value = key
    filterModalLabel.value = label
    filterModalValues.value = distinctValues(key)
    filterModalSelected.value = new Set(columnMultiFilters.value.get(key) ?? [])
    filterModalIncludeEmpty.value = columnEmptyFilters.value.has(key)
    filterModalOpen.value = true
  }

  function applyColumnFilter(selected: Set<string>, includeEmpty: boolean) {
    const key = filterModalColumn.value
    const newMap = new Map(columnMultiFilters.value)
    if (selected.size > 0) { newMap.set(key, selected) } else { newMap.delete(key) }
    columnMultiFilters.value = newMap
    const newEmpty = new Set(columnEmptyFilters.value)
    if (includeEmpty) { newEmpty.add(key) } else { newEmpty.delete(key) }
    columnEmptyFilters.value = newEmpty
  }

  return reactive({
    fieldDefs,
    searchText,
    filteredItems,
    visibleColumns,
    visibleFieldColumns,
    pickerOptions,
    isColumnVisible,
    toggleColumn,
    columnValue,
    sortIcon,
    toggleSort,
    hasActiveFilter,
    openFilterModal,
    applyColumnFilter,
    filterModalOpen,
    filterModalLabel,
    filterModalValues,
    filterModalSelected,
    filterModalIncludeEmpty,
  })
}

export type ItemTableApi = ReturnType<typeof useItemTable>
