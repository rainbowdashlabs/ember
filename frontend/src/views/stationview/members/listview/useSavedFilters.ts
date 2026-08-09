/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { savedFilters as savedFiltersApi } from '@/api'
import type { SortDirection } from '@/composables/useSortable'

export type FilterKey = 'name' | 'groups' | 'tags' | number

export type MemberSortKey = 'name' | number

export interface TabFilterState {
  filterText: string
  columnMultiFilters: Map<FilterKey, Set<string>>
  columnEmptyFilters: Set<FilterKey>
  sortKey: MemberSortKey
  sortDirection: SortDirection
}

export function emptyTabState(): TabFilterState {
  return { filterText: '', columnMultiFilters: new Map(), columnEmptyFilters: new Set(), sortKey: 'name', sortDirection: 'asc' }
}

export interface SavedFilterPreset {
  id?: number
  name: string
  tab: string
  textFilters: Record<string, string>
  multiFilters: Record<string, string[]>
  emptyFilters?: string[]
}

const TABLE_TYPE = 'members'

export function useSavedFilters(tabStates: Ref<Record<string, TabFilterState>>, activeTab: Ref<string>) {
  const savedFilters = ref<SavedFilterPreset[]>([])

  async function loadSavedFilters() {
    try {
      const filters = await savedFiltersApi.listFilters(TABLE_TYPE)
      savedFilters.value = filters.map(f => {
        const data = JSON.parse(f.filterData)
        return { id: f.id, name: f.name, tab: data.tab ?? 'ALL', textFilters: data.textFilters ?? {}, multiFilters: data.multiFilters ?? {} }
      })
    } catch { /* ignore */ }
  }

  async function saveCurrentFilter(name: string) {
    const state = tabStates.value[activeTab.value]
    if (!state) return
    const textFilters: Record<string, string> = {}
    const multiFilters: Record<string, string[]> = {}
    for (const [k, v] of state.columnMultiFilters) { multiFilters[String(k)] = [...v] }
    const emptyFilters: string[] = [...state.columnEmptyFilters].map(String)
    const filterData = JSON.stringify({ tab: activeTab.value, textFilters, multiFilters, emptyFilters })
    try {
      await savedFiltersApi.createFilter({ tableType: TABLE_TYPE, name, filterData })
      await loadSavedFilters()
    } catch { /* ignore */ }
  }

  function applyFilter(preset: SavedFilterPreset) {
    const state = tabStates.value[preset.tab]
    if (!state) return
    activeTab.value = preset.tab
    const multiMap = new Map<FilterKey, Set<string>>()
    for (const [k, v] of Object.entries(preset.multiFilters)) {
      const key: FilterKey = (k === 'name' || k === 'groups' || k === 'tags') ? k : Number(k)
      multiMap.set(key, new Set(v))
    }
    state.columnMultiFilters = multiMap
    const emptySet = new Set<FilterKey>()
    for (const k of (preset.emptyFilters ?? [])) {
      emptySet.add((k === 'name' || k === 'groups' || k === 'tags') ? k : Number(k))
    }
    state.columnEmptyFilters = emptySet
  }

  async function deleteFilter(index: number) {
    const preset = savedFilters.value[index]
    if (!preset) return
    try {
      await savedFiltersApi.deleteFilter(preset.id!)
      await loadSavedFilters()
    } catch { /* ignore */ }
  }

  function clearFilters() {
    const state = tabStates.value[activeTab.value]
    if (!state) return
    state.columnMultiFilters = new Map()
    state.columnEmptyFilters = new Set()
    state.filterText = ''
  }

  return {
    savedFilters,
    loadSavedFilters,
    saveCurrentFilter,
    applyFilter,
    deleteFilter,
    clearFilters,
  }
}
