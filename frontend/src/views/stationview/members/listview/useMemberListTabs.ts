/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { StationUserType } from '@/api/types'
import { parseFieldConfig, type ProfileField } from '@/api/profileFields'
import { emptyTabState, type MemberSortKey, type TabFilterState } from './useSavedFilters'
import type { SortDirection } from '@/composables/useSortable'

const TAB_KEYS = ['ALL', StationUserType.TRIAL, StationUserType.MEMBER, StationUserType.GUARDIAN,
  StationUserType.TEAM, StationUserType.MANAGER] as const

/**
 * The member list's tabs and the per-tab view state behind them.
 *
 * Filters, sorting and column choices are kept per tab rather than shared, because the tabs show
 * different populations with different fields - a filter that makes sense for trial members is
 * meaningless on the manager tab. Switching tabs therefore restores what that tab last looked
 * like instead of carrying the previous tab's narrowing across.
 *
 * @param fields every profile field, filtered here down to the ones the active tab can show
 */
export function useMemberListTabs(fields: Ref<ProfileField[]>) {
  const { t } = useI18n()

  const activeTab = ref<string>('ALL')

  const tabStates = ref<Record<string, TabFilterState>>(
    Object.fromEntries(TAB_KEYS.map(key => [key, emptyTabState()])),
  )

  const currentTabState = computed(() => tabStates.value[activeTab.value] ?? emptyTabState())

  /** Mutable state of the active tab. Falls back to a throwaway state for unknown tabs. */
  function activeTabState(): TabFilterState {
    return tabStates.value[activeTab.value] ?? emptyTabState()
  }

  const filterText = computed({
    get: () => currentTabState.value.filterText,
    set: (v: string) => { activeTabState().filterText = v },
  })
  const columnMultiFilters = computed(() => currentTabState.value.columnMultiFilters)
  const columnEmptyFilters = computed(() => currentTabState.value.columnEmptyFilters)
  const sortKey = computed<MemberSortKey>({
    get: () => currentTabState.value.sortKey,
    set: (v: MemberSortKey) => { activeTabState().sortKey = v },
  })
  const sortDirection = computed<SortDirection>({
    get: () => currentTabState.value.sortDirection,
    set: (v: SortDirection) => { activeTabState().sortDirection = v },
  })

  const tabs = computed(() => [
    { key: 'ALL', label: t('membersList.tabAll') },
    { key: StationUserType.TRIAL, label: t('membersList.tabTrial') },
    { key: StationUserType.MEMBER, label: t('membersList.tabMember') },
    { key: StationUserType.GUARDIAN, label: t('membersList.tabMemberManager') },
    { key: StationUserType.TEAM, label: t('membersList.tabTeam') },
    { key: StationUserType.MANAGER, label: t('membersList.tabManager') },
  ])

  const extraColumnIds = ref<Set<number>>(new Set())
  const hiddenColumnIds = ref<Set<number>>(new Set())

  /**
   * The fields the active tab may show. The "all" tab spans every member type; a type tab is
   * limited to its own. Group-scoped fields never appear - they belong to a group, not a member.
   */
  const tabScopedFields = computed(() => {
    const scopes: string[] = activeTab.value === 'ALL'
      ? [StationUserType.TRIAL, StationUserType.MEMBER, StationUserType.GUARDIAN,
        StationUserType.TEAM, StationUserType.MANAGER]
      : [activeTab.value]
    return fields.value.filter(f =>
      f.scope !== 'GROUP' && scopes.includes(f.scope ?? StationUserType.MEMBER))
  })

  const tabOverviewFields = computed(() =>
    tabScopedFields.value.filter(f => parseFieldConfig(f.config).overview))
  const tabNonOverviewFields = computed(() =>
    tabScopedFields.value.filter(f => !parseFieldConfig(f.config).overview))

  /**
   * Overview fields are shown unless hidden; the rest only when explicitly added, so the table
   * stays readable while every field remains reachable.
   */
  const visibleColumns = computed(() => [
    ...tabOverviewFields.value.filter(f => !hiddenColumnIds.value.has(f.id)),
    ...tabNonOverviewFields.value.filter(f => extraColumnIds.value.has(f.id)),
  ])

  /**
   * Narrows a column to the selected values on the active tab. An empty selection clears the
   * filter; {@code includeEmpty} additionally keeps the members that have no value at all.
   */
  function applyColumnFilter(
    key: 'name' | 'groups' | 'tags' | number,
    selected: Set<string>,
    includeEmpty: boolean,
  ) {
    const state = activeTabState()
    const values = new Map(state.columnMultiFilters)
    if (selected.size) values.set(key, selected)
    else values.delete(key)
    state.columnMultiFilters = values

    const empties = new Set(state.columnEmptyFilters)
    if (includeEmpty) empties.add(key)
    else empties.delete(key)
    state.columnEmptyFilters = empties
  }

  function toggleColumn(fieldId: number) {
    const target = tabOverviewFields.value.some(f => f.id === fieldId)
      ? hiddenColumnIds
      : extraColumnIds
    const next = new Set(target.value)
    if (next.has(fieldId)) next.delete(fieldId)
    else next.add(fieldId)
    target.value = next
  }

  return {
    activeTab,
    tabStates,
    tabs,
    filterText,
    columnMultiFilters,
    columnEmptyFilters,
    sortKey,
    sortDirection,
    extraColumnIds,
    hiddenColumnIds,
    tabScopedFields,
    tabOverviewFields,
    tabNonOverviewFields,
    visibleColumns,
    toggleColumn,
    applyColumnFilter,
  }
}
