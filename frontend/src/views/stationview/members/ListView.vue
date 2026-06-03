/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import MemberFilterBar from './listview/FilterBar.vue'
import MemberTable from './listview/Table.vue'
import ExportModal from './listview/ExportModal.vue'
import type { StationMember } from '@/api/types'
import { StationUserType } from '@/api/types'
import type { FilterCriteria, FilterOption } from '@/components/input/filter/MemberFilterBar.vue'
import { useMemberData, parseConfig, memberDisplayName } from './listview/useMemberData'
import { useSavedFilters, emptyTabState, type TabFilterState } from './listview/useSavedFilters'
import { useExport } from './listview/useExport'

const { t } = useI18n()
const router = useRouter()

// --- Member data ---
const {
  members, fields, allGroups, allTags, allRoles,
  memberRolesMap, memberGroupsMap, memberTagsMap, memberManagers,
  loading, error, expandedId, overviewFields,
  getFieldValue, getFieldValueAsString, getMemberType, getMemberGroups, getColumnValues,
  loadData, toggleExpand,
} = useMemberData()

// --- Tab state ---
const activeTab = ref('ALL')
const memberFilterCriteria = ref<FilterCriteria>({ roleIds: [], groupIds: [], tagIds: [], mode: 'AND' })

const tabStates = ref<Record<string, TabFilterState>>({
  ALL: emptyTabState(),
  MEMBER: emptyTabState(),
  GUARDIAN: emptyTabState(),
  TEAM: emptyTabState(),
})

const currentTabState = computed(() => tabStates.value[activeTab.value] ?? emptyTabState())
const filterText = computed({
  get: () => currentTabState.value.filterText,
  set: (v: string) => { tabStates.value[activeTab.value].filterText = v },
})
const columnMultiFilters = computed(() => currentTabState.value.columnMultiFilters)
const columnEmptyFilters = computed(() => currentTabState.value.columnEmptyFilters)
const sortColumn = computed(() => currentTabState.value.sortColumn)
const sortAsc = computed(() => currentTabState.value.sortAsc)

const tabs = computed(() => [
  { key: 'ALL', label: t('membersList.tabAll') },
  { key: 'MEMBER', label: t('membersList.tabMember') },
  { key: 'GUARDIAN', label: t('membersList.tabMemberManager') },
  { key: 'TEAM', label: t('membersList.tabTeam') },
])

// --- Saved filters ---
const { savedFilters, loadSavedFilters, saveCurrentFilter, applyFilter, deleteFilter, clearFilters } =
  useSavedFilters(tabStates, activeTab)

// --- Column visibility ---
const extraColumnIds = ref<Set<number>>(new Set())

const tabScopedFields = computed(() => {
  const scopeForTab: Record<string, string[]> = {
    ALL: [StationUserType.MEMBER, StationUserType.GUARDIAN, StationUserType.TEAM],
    [StationUserType.MEMBER]: [StationUserType.MEMBER],
    [StationUserType.GUARDIAN]: [StationUserType.GUARDIAN],
    [StationUserType.TEAM]: [StationUserType.TEAM],
  }
  const scopes = scopeForTab[activeTab.value] ?? []
  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return scopes.includes(f.scope ?? StationUserType.MEMBER)
  })
})

const tabOverviewFields = computed(() => tabScopedFields.value.filter(f => parseConfig(f.config).overview))
const tabNonOverviewFields = computed(() => tabScopedFields.value.filter(f => !parseConfig(f.config).overview))

const visibleColumns = computed(() => {
  const extra = tabNonOverviewFields.value.filter(f => extraColumnIds.value.has(f.id))
  return [...tabOverviewFields.value, ...extra]
})

function toggleExtraColumn(fieldId: number) {
  const newSet = new Set(extraColumnIds.value)
  if (newSet.has(fieldId)) { newSet.delete(fieldId) } else { newSet.add(fieldId) }
  extraColumnIds.value = newSet
}

// --- Filter bar options ---
const roleFriendlyNames: Record<string, string> = {
  MEMBER: 'Mitglied', GUARDIAN: 'Erziehungsberechtigter', TEAM: 'Team', TRIAL: 'Probe',
}
const filterRoleOptions = computed<FilterOption[]>(() => {
  const allowed: string[] = [StationUserType.MEMBER, StationUserType.GUARDIAN, StationUserType.TEAM, StationUserType.TRIAL]
  return allRoles.value.filter(r => allowed.includes(r.permission)).map(r => ({ id: r.id, name: roleFriendlyNames[r.permission] ?? r.permission }))
})
const filterGroupOptions = computed<FilterOption[]>(() => allGroups.value.map(g => ({ id: g.id, name: g.name ?? '' })))
const filterTagOptions = computed<FilterOption[]>(() => allTags.value.map(t => ({ id: t.id, name: t.name })))

function onMemberFilter(criteria: FilterCriteria) {
  memberFilterCriteria.value = criteria
}

// --- Filtered and sorted members ---
const filteredMembers = computed(() => {
  let list = activeTab.value === 'ALL' ? members.value : members.value.filter(m => getMemberType(m.id) === activeTab.value)

  const fc = memberFilterCriteria.value
  if (fc.roleIds.length > 0 || fc.groupIds.length > 0 || fc.tagIds.length > 0) {
    const filterRoleNames = new Set<string>(allRoles.value.filter(r => fc.roleIds.includes(r.id)).map(r => r.permission))
    const filterGroupNames = new Set(allGroups.value.filter(g => fc.groupIds.includes(g.id)).map(g => g.name ?? ''))
    const filterTagNames = new Set(allTags.value.filter(t => fc.tagIds.includes(t.id)).map(t => t.name))

    list = list.filter(m => {
      const matchesRole = fc.roleIds.length === 0 || (memberRolesMap.value.get(m.id) ?? []).some(r => filterRoleNames.has(r))
      const matchesGroup = fc.groupIds.length === 0 || (memberGroupsMap.value.get(m.id) ?? []).some(g => filterGroupNames.has(g))
      const matchesTag = fc.tagIds.length === 0 || (memberTagsMap.value.get(m.id) ?? []).some(t => filterTagNames.has(t))
      return fc.mode === 'AND' ? (matchesRole && matchesGroup && matchesTag) : (matchesRole || matchesGroup || matchesTag)
    })
  }

  const q = filterText.value.toLowerCase().trim()
  if (q) {
    list = list.filter(m => {
      if (memberDisplayName(m).toLowerCase().includes(q)) return true
      if ((m.email ?? '').toLowerCase().includes(q)) return true
      for (const f of overviewFields.value) {
        if (getFieldValueAsString(m.id, f.id).toLowerCase().includes(q)) return true
      }
      return false
    })
  }
  for (const [key, selectedValues] of columnMultiFilters.value) {
    if (selectedValues.size === 0) continue
    const includeEmpty = columnEmptyFilters.value.has(key)
    list = list.filter(m => {
      const values = getColumnValues(m, key)
      if (values.length === 0 || values.every(v => !v)) return includeEmpty
      return values.some(v => selectedValues.has(v))
    })
  }
  for (const key of columnEmptyFilters.value) {
    if (columnMultiFilters.value.has(key) && (columnMultiFilters.value.get(key)?.size ?? 0) > 0) continue
    list = list.filter(m => {
      const values = getColumnValues(m, key)
      return values.length === 0 || values.every(v => !v)
    })
  }
  return [...list].sort((a, b) => {
    let valA: string, valB: string
    if (sortColumn.value === 'name') {
      valA = memberDisplayName(a).toLowerCase()
      valB = memberDisplayName(b).toLowerCase()
    } else {
      valA = getFieldValueAsString(a.id, sortColumn.value).toLowerCase()
      valB = getFieldValueAsString(b.id, sortColumn.value).toLowerCase()
    }
    const cmp = valA.localeCompare(valB)
    return sortAsc.value ? cmp : -cmp
  })
})

// --- Sort & column filter actions ---
function toggleSort(column: 'name' | number) {
  const state = tabStates.value[activeTab.value]
  if (state.sortColumn === column) { state.sortAsc = !state.sortAsc }
  else { state.sortColumn = column; state.sortAsc = true }
}

function applyColumnFilter(key: 'name' | 'groups' | 'tags' | number, selected: Set<string>, includeEmpty: boolean) {
  const state = tabStates.value[activeTab.value]
  const newMap = new Map(state.columnMultiFilters)
  if (selected.size > 0) { newMap.set(key, selected) } else { newMap.delete(key) }
  state.columnMultiFilters = newMap
  const newEmpty = new Set(state.columnEmptyFilters)
  if (includeEmpty) { newEmpty.add(key) } else { newEmpty.delete(key) }
  state.columnEmptyFilters = newEmpty
}

// --- Export ---
const {
  exportMode, selectedIds, showExportModal,
  toggleExportMode, toggleSelect, toggleSelectAll, openExportModal, performExport,
} = useExport(filteredMembers, fields, getMemberGroups, getFieldValueAsString)

// --- Navigation ---
function navigateToDetail(member: StationMember, event: Event) {
  event.stopPropagation()
  router.push({ name: 'members-detail', params: { id: member.id } })
}

function navigateToEdit(member: StationMember, event: Event) {
  event.stopPropagation()
  router.push({ name: 'members-edit', params: { id: member.id } })
}

onMounted(() => {
  loadData()
  loadSavedFilters()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <TabBar v-model="activeTab" :tabs="tabs" />

        <MemberFilterBar
          v-model:filter-text="filterText"
          :saved-filters="savedFilters"
          :non-overview-fields="tabNonOverviewFields"
          :extra-column-ids="extraColumnIds"
          :export-mode="exportMode"
          :selected-count="selectedIds.size"
          :roles="filterRoleOptions"
          :groups="filterGroupOptions"
          :tags="filterTagOptions"
          @clear-filters="clearFilters"
          @apply-filter="applyFilter"
          @delete-filter="deleteFilter"
          @save-filter="saveCurrentFilter"
          @toggle-column="toggleExtraColumn"
          @toggle-export="toggleExportMode"
          @export-continue="openExportModal"
          @filter="onMemberFilter"
        />

        <MemberTable
          :members="filteredMembers"
          :visible-columns="visibleColumns"
          :expanded-id="expandedId"
          :sort-column="sortColumn"
          :sort-asc="sortAsc"
          :column-multi-filters="columnMultiFilters"
          :column-empty-filters="columnEmptyFilters"
          :member-groups-map="memberGroupsMap"
          :member-tags-map="memberTagsMap"
          :member-roles-map="memberRolesMap"
          :member-managers="memberManagers"
          :all-members="members"
          :overview-fields="overviewFields"
          :get-field-value="getFieldValue"
          :export-mode="exportMode"
          :selected-ids="selectedIds"
          @toggle-sort="toggleSort"
          @apply-column-filter="applyColumnFilter"
          @toggle-expand="toggleExpand"
          @navigate-detail="navigateToDetail"
          @navigate-edit="navigateToEdit"
          @toggle-select="toggleSelect"
          @toggle-select-all="toggleSelectAll"
        />
      </template>

      <ExportModal
        v-model="showExportModal"
        :available-fields="tabScopedFields"
        :selected-count="selectedIds.size"
        @export="performExport"
      />
    </div>
  </ViewContent>
</template>
