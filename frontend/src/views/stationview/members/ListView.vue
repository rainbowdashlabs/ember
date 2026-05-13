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
import type { ProfileField, StationMember, MemberGroup } from '@/api/types'
import { Roles, hasTeamRole } from '@/api/types'
import { profileFields, stationMembers, memberGroups, savedFilters as savedFiltersApi } from '@/api'
import { useStations } from '@/composables/useStations'

const { t } = useI18n()
const router = useRouter()
const { currentStationId } = useStations()

const members = ref<StationMember[]>([])
const fields = ref<ProfileField[]>([])
const allGroups = ref<MemberGroup[]>([])
const memberValues = ref<Map<number, Map<number, string>>>(new Map())
const memberRolesMap = ref<Map<number, string[]>>(new Map())
const memberGroupsMap = ref<Map<number, string[]>>(new Map())
const memberManagers = ref<Map<number, StationMember[]>>(new Map())
const loading = ref(true)
const error = ref('')
const expandedId = ref<number | null>(null)
const activeTab = ref('ALL')
const filterText = ref('')
const columnFilters = ref<Map<'name' | 'groups' | number, string>>(new Map())
const columnMultiFilters = ref<Map<'groups' | number, Set<string>>>(new Map())
const sortColumn = ref<'name' | number>('name')
const sortAsc = ref(true)

// Export mode
const exportMode = ref(false)
const selectedIds = ref<Set<number>>(new Set())
const showExportModal = ref(false)

const tabs = computed(() => [
  { key: 'ALL', label: t('membersList.tabAll') },
  { key: 'MEMBER', label: t('membersList.tabMember') },
  { key: 'MEMBER_MANAGER', label: t('membersList.tabMemberManager') },
  { key: 'TEAM', label: t('membersList.tabTeam') },
])

// Saved filters
const TABLE_TYPE = 'members'

interface SavedFilterPreset {
  id?: number
  name: string
  tab: string
  textFilters: Record<string, string>
  multiFilters: Record<string, string[]>
}

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
  const textFilters: Record<string, string> = {}
  for (const [k, v] of columnFilters.value) { textFilters[String(k)] = v }
  const multiFilters: Record<string, string[]> = {}
  for (const [k, v] of columnMultiFilters.value) { multiFilters[String(k)] = [...v] }
  const filterData = JSON.stringify({ tab: activeTab.value, textFilters, multiFilters })
  try {
    await savedFiltersApi.createFilter({ tableType: TABLE_TYPE, name, filterData })
    await loadSavedFilters()
  } catch { /* ignore */ }
}

function applyFilter(preset: SavedFilterPreset) {
  activeTab.value = preset.tab
  const textMap = new Map<'name' | 'groups' | number, string>()
  for (const [k, v] of Object.entries(preset.textFilters)) {
    textMap.set(k === 'name' || k === 'groups' ? k : Number(k), v)
  }
  columnFilters.value = textMap
  const multiMap = new Map<'groups' | number, Set<string>>()
  for (const [k, v] of Object.entries(preset.multiFilters)) {
    multiMap.set(k === 'groups' ? k : Number(k), new Set(v))
  }
  columnMultiFilters.value = multiMap
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
  columnFilters.value = new Map()
  columnMultiFilters.value = new Map()
  filterText.value = ''
}

function parseConfig(configStr: string | undefined): Record<string, unknown> {
  if (!configStr) return {}
  try { return JSON.parse(configStr) } catch { return {} }
}

function computeAge(dateStr: string, mode: string): string {
  if (!dateStr) return ''
  const birth = new Date(dateStr)
  const now = new Date()
  const target = mode === 'end_of_year' ? new Date(now.getFullYear(), 11, 31) : now
  let age = target.getFullYear() - birth.getFullYear()
  const m = target.getMonth() - birth.getMonth()
  if (m < 0 || (m === 0 && target.getDate() < birth.getDate())) age--
  return String(age)
}

const overviewFields = computed(() => fields.value.filter(f => parseConfig(f.config).overview))
const extraColumnIds = ref<Set<number>>(new Set())

const tabScopedFields = computed(() => {
  const scopeForTab: Record<string, string[]> = {
    ALL: [Roles.MEMBER, Roles.MEMBER_MANAGER, Roles.TEAM],
    [Roles.MEMBER]: [Roles.MEMBER],
    [Roles.MEMBER_MANAGER]: [Roles.MEMBER_MANAGER],
    [Roles.TEAM]: [Roles.TEAM],
  }
  const scopes = scopeForTab[activeTab.value] ?? []
  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return scopes.includes(f.scope ?? Roles.MEMBER)
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

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function getMemberFirstName(m: StationMember): string {
  const name = m.name ?? ''
  return name.split(' ')[0] ?? ''
}

function getMemberLastName(m: StationMember): string {
  const name = m.name ?? ''
  return name.split(' ').slice(1).join(' ') ?? ''
}

function getFieldValue(memberId: number, fieldId: number): string {
  const field = fields.value.find(f => f.id === fieldId)
  if (field?.fieldType === 'age') {
    const cfg = parseConfig(field.config)
    const sourceField = fields.value.find(f => f.name === cfg.sourceField)
    if (sourceField) {
      const dateVal = getRawFieldValue(memberId, sourceField.id)
      return computeAge(dateVal, (cfg.ageMode as string) ?? 'now')
    }
    return ''
  }
  return getRawFieldValue(memberId, fieldId)
}

function getRawFieldValue(memberId: number, fieldId: number): string {
  const vals = memberValues.value.get(memberId)
  if (!vals) return ''
  const raw = vals.get(fieldId) ?? ''
  try { return JSON.parse(raw) } catch { return raw }
}

function getMemberType(memberId: number): 'MEMBER' | 'MEMBER_MANAGER' | 'TEAM' | null {
  const roles = memberRolesMap.value.get(memberId) ?? []
  if (hasTeamRole(roles)) return Roles.TEAM
  if (roles.includes(Roles.MEMBER_MANAGER)) return Roles.MEMBER_MANAGER
  if (roles.includes(Roles.MEMBER)) return Roles.MEMBER
  return null
}

function getMemberGroups(memberId: number): string[] {
  return memberGroupsMap.value.get(memberId) ?? []
}

const filteredMembers = computed(() => {
  let list = activeTab.value === 'ALL' ? members.value : members.value.filter(m => getMemberType(m.id) === activeTab.value)
  const q = filterText.value.toLowerCase().trim()
  if (q) {
    list = list.filter(m => {
      if (memberDisplayName(m).toLowerCase().includes(q)) return true
      if ((m.email ?? '').toLowerCase().includes(q)) return true
      for (const f of overviewFields.value) {
        if (getFieldValue(m.id, f.id).toLowerCase().includes(q)) return true
      }
      return false
    })
  }
  for (const [column, filter] of columnFilters.value) {
    const f = filter.toLowerCase().trim()
    if (!f) continue
    list = list.filter(m => {
      if (column === 'name') return memberDisplayName(m).toLowerCase().includes(f)
      if (column === 'groups') return getMemberGroups(m.id).some(g => g.toLowerCase().includes(f))
      return getFieldValue(m.id, column as number).toLowerCase().includes(f)
    })
  }
  for (const [key, selectedValues] of columnMultiFilters.value) {
    if (selectedValues.size === 0) continue
    if (key === 'groups') {
      list = list.filter(m => getMemberGroups(m.id).some(g => selectedValues.has(g)))
    } else {
      list = list.filter(m => selectedValues.has(getFieldValue(m.id, key as number)))
    }
  }
  return [...list].sort((a, b) => {
    let valA: string, valB: string
    if (sortColumn.value === 'name') {
      valA = memberDisplayName(a).toLowerCase()
      valB = memberDisplayName(b).toLowerCase()
    } else {
      valA = getFieldValue(a.id, sortColumn.value).toLowerCase()
      valB = getFieldValue(b.id, sortColumn.value).toLowerCase()
    }
    const cmp = valA.localeCompare(valB)
    return sortAsc.value ? cmp : -cmp
  })
})

function toggleSort(column: 'name' | number) {
  if (sortColumn.value === column) { sortAsc.value = !sortAsc.value }
  else { sortColumn.value = column; sortAsc.value = true }
}

function setColumnFilter(column: 'name' | number, value: string) {
  const newMap = new Map(columnFilters.value)
  if (value.trim()) { newMap.set(column, value) } else { newMap.delete(column) }
  columnFilters.value = newMap
}

function toggleMultiFilter(key: 'groups' | number, value: string) {
  const current = new Set<string>(columnMultiFilters.value.get(key) ?? [])
  if (current.has(value)) { current.delete(value) } else { current.add(value) }
  const newMap = new Map(columnMultiFilters.value)
  if (current.size > 0) { newMap.set(key, current) } else { newMap.delete(key) }
  columnMultiFilters.value = newMap
}

function navigateToDetail(member: StationMember, event: Event) {
  event.stopPropagation()
  router.push({ name: 'members-detail', params: { id: member.id } })
}

function navigateToEdit(member: StationMember, event: Event) {
  event.stopPropagation()
  router.push({ name: 'members-edit', params: { id: member.id } })
}

// Export functions
function toggleExportMode() {
  exportMode.value = !exportMode.value
  if (!exportMode.value) {
    selectedIds.value = new Set()
  }
}

function toggleSelect(memberId: number) {
  const newSet = new Set(selectedIds.value)
  if (newSet.has(memberId)) { newSet.delete(memberId) } else { newSet.add(memberId) }
  selectedIds.value = newSet
}

function toggleSelectAll() {
  if (filteredMembers.value.every(m => selectedIds.value.has(m.id))) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(filteredMembers.value.map(m => m.id))
  }
}

function openExportModal() {
  if (selectedIds.value.size === 0) return
  showExportModal.value = true
}

function performExport(columns: string[], format: 'csv' | 'values') {
  const selectedMembers = filteredMembers.value.filter(m => selectedIds.value.has(m.id))

  function getColumnValue(m: StationMember, col: string): string {
    if (col === 'firstName') return getMemberFirstName(m)
    if (col === 'lastName') return getMemberLastName(m)
    if (col === 'email') return m.email ?? ''
    if (col === 'groups') return getMemberGroups(m.id).join(', ')
    if (col.startsWith('field:')) {
      const fieldId = Number(col.slice(6))
      return getFieldValue(m.id, fieldId)
    }
    return ''
  }

  function getColumnLabel(col: string): string {
    if (col === 'firstName') return t('membersList.export.colFirstName')
    if (col === 'lastName') return t('membersList.export.colLastName')
    if (col === 'email') return t('membersList.export.colEmail')
    if (col === 'groups') return t('membersList.export.colGroups')
    if (col.startsWith('field:')) {
      const fieldId = Number(col.slice(6))
      return fields.value.find(f => f.id === fieldId)?.name ?? ''
    }
    return col
  }

  let output: string

  if (format === 'values' && columns.length === 1) {
    const col = columns[0]
    const values = selectedMembers.map(m => getColumnValue(m, col)).filter(v => v)
    output = values.join('; ')
  } else {
    const escapeCsv = (val: string) => {
      if (val.includes(';') || val.includes('"') || val.includes('\n')) {
        return `"${val.replace(/"/g, '""')}"`
      }
      return val
    }
    const header = columns.map(c => escapeCsv(getColumnLabel(c))).join(';')
    const rows = selectedMembers.map(m =>
      columns.map(c => escapeCsv(getColumnValue(m, c))).join(';')
    )
    output = [header, ...rows].join('\n')
  }

  // Download as file
  const blob = new Blob([output], { type: format === 'csv' ? 'text/csv;charset=utf-8' : 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = format === 'csv' ? 'mitglieder.csv' : 'mitglieder.txt'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)

  showExportModal.value = false
  exportMode.value = false
  selectedIds.value = new Set()
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [allFields, allMembers, groups] = await Promise.all([
      profileFields.listFields(),
      stationMembers.listMembers(currentStationId.value!),
      memberGroups.listGroups(),
    ])
    fields.value = allFields
    members.value = allMembers
    allGroups.value = groups

    const valMap = new Map<number, Map<number, string>>()
    const rolesMap = new Map<number, string[]>()
    const groupsMap = new Map<number, string[]>()
    for (const m of allMembers) {
      try {
        const [vals, roles] = await Promise.all([
          profileFields.getValues(m.id),
          stationMembers.getRoles(m.id),
        ])
        const fieldMap = new Map<number, string>()
        for (const v of vals) { fieldMap.set(v.fieldId, v.value ?? '') }
        valMap.set(m.id, fieldMap)
        rolesMap.set(m.id, roles.map(r => r.role))
      } catch { /* skip */ }
    }
    for (const group of groups) {
      try {
        const groupMembers = await memberGroups.getGroupMembers(group.id)
        for (const gm of groupMembers) {
          const existing = groupsMap.get(gm.id) ?? []
          existing.push(group.name ?? '')
          groupsMap.set(gm.id, existing)
        }
      } catch { /* skip */ }
    }
    memberValues.value = valMap
    memberRolesMap.value = rolesMap
    memberGroupsMap.value = groupsMap
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function toggleExpand(member: StationMember) {
  if (expandedId.value === member.id) { expandedId.value = null; return }
  expandedId.value = member.id
  if (!memberManagers.value.has(member.id)) {
    try {
      const managers = await stationMembers.getManagers(member.id)
      memberManagers.value = new Map([...memberManagers.value, [member.id, managers]])
    } catch { /* ignore */ }
  }
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
          @clear-filters="clearFilters"
          @apply-filter="applyFilter"
          @delete-filter="deleteFilter"
          @save-filter="saveCurrentFilter"
          @toggle-column="toggleExtraColumn"
          @toggle-export="toggleExportMode"
          @export-continue="openExportModal"
        />

        <MemberTable
          :members="filteredMembers"
          :visible-columns="visibleColumns"
          :expanded-id="expandedId"
          :sort-column="sortColumn"
          :sort-asc="sortAsc"
          :column-filters="columnFilters"
          :column-multi-filters="columnMultiFilters"
          :member-groups-map="memberGroupsMap"
          :member-roles-map="memberRolesMap"
          :member-managers="memberManagers"
          :all-members="members"
          :overview-fields="overviewFields"
          :get-field-value="getFieldValue"
          :export-mode="exportMode"
          :selected-ids="selectedIds"
          @toggle-sort="toggleSort"
          @set-column-filter="setColumnFilter"
          @toggle-multi-filter="toggleMultiFilter"
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
