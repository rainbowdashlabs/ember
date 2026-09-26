/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { savedFilters as savedFiltersApi } from '@/api'
import type { DataTableState } from '@/composables/useDataTable'
import { describeFailure, type Failure } from '@/util/failure'

export interface SavedFilterPreset {
  id?: number
  name: string
  tab: string
  multiFilters: Record<string, string[]>
  emptyFilters: string[]
}

const TABLE_TYPE = 'members'

/**
 * A saved filter as it was stored, read defensively: an older one may lack the empty filters.
 *
 * <p>The empty filters were written from the start but dropped on the way back in, so a saved
 * "this field is empty" came back without that condition. They are read here like everything else.
 */
export function presetOf(id: number, name: string, filterData: string): SavedFilterPreset {
  const data = JSON.parse(filterData)
  return {
    id,
    name,
    tab: data.tab ?? 'ALL',
    multiFilters: data.multiFilters ?? {},
    emptyFilters: Array.isArray(data.emptyFilters) ? data.emptyFilters.map(String) : [],
  }
}

/**
 * The member list's saved filters: the filters of one tab kept under a name on the server.
 *
 * <p>Columns are stored by the key the table names them by, which for a profile field is its id.
 */
export function useSavedFilters(tabStates: Ref<Record<string, DataTableState>>, activeTab: Ref<string>) {
  const {t} = useI18n()

  const savedFilters = ref<SavedFilterPreset[]>([])

  /** What saving or removing a filter ran into, which used to be swallowed whole. */
  const filterFailure = ref<Failure | null>(null)

  async function loadSavedFilters() {
    try {
      const filters = await savedFiltersApi.listFilters(TABLE_TYPE)
      savedFilters.value = filters.map(f => presetOf(f.id, f.name, f.filterData))
    } catch (e) {
      savedFilters.value = []
      filterFailure.value = {...describeFailure(e, t), message: t('membersList.savedFiltersUnreadable')}
    }
  }

  async function saveCurrentFilter(name: string) {
    const state = tabStates.value[activeTab.value]
    if (!state) return
    const multiFilters: Record<string, string[]> = {}
    for (const [key, values] of state.filters) multiFilters[key] = [...values]
    const emptyFilters = [...state.empties]
    const filterData = JSON.stringify({ tab: activeTab.value, textFilters: {}, multiFilters, emptyFilters })
    filterFailure.value = null
    try {
      await savedFiltersApi.createFilter({ tableType: TABLE_TYPE, name, filterData })
    } catch (e) {
      filterFailure.value = describeFailure(e, t)
      return
    }
    await loadSavedFilters()
  }

  function applyFilter(preset: SavedFilterPreset) {
    const state = tabStates.value[preset.tab]
    if (!state) return
    activeTab.value = preset.tab
    state.filters = new Map(Object.entries(preset.multiFilters).map(([key, values]) => [key, new Set(values)]))
    state.empties = new Set(preset.emptyFilters)
  }

  async function deleteFilter(index: number) {
    const preset = savedFilters.value[index]
    if (preset?.id === undefined) return
    filterFailure.value = null
    try {
      await savedFiltersApi.deleteFilter(preset.id)
    } catch (e) {
      filterFailure.value = describeFailure(e, t)
      return
    }
    await loadSavedFilters()
  }

  function clearFilters() {
    const state = tabStates.value[activeTab.value]
    if (!state) return
    state.filters = new Map()
    state.empties = new Set()
    state.search = ''
  }

  return {
    savedFilters,
    filterFailure,
    loadSavedFilters,
    saveCurrentFilter,
    applyFilter,
    deleteFilter,
    clearFilters,
  }
}
