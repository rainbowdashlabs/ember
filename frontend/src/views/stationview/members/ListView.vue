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
import ListViewBody from './listview/ListViewBody.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import type { StationMember } from '@/api/types'
import { StationPermission, StationUserType, parseFieldConfig } from '@/api/types'
import { useMemberData, memberDisplayName } from './listview/useMemberData'
import { useSavedFilters, emptyTabState, type TabFilterState } from './listview/useSavedFilters'
import { useExport } from './listview/useExport'
import { useMemberFilter } from '@/composables/useMemberFilter'
import { useSession } from '@/composables/useSession'
import { stationMembers } from '@/api'

const { t } = useI18n()
const router = useRouter()
const { hasPermission } = useSession()
const canExport = computed(() => hasPermission(StationPermission.MEMBER_EXPORT))
const canEdit = computed(() => hasPermission(StationPermission.MEMBER_EDIT))

const {
  members, fields, allGroups, allTags,
  memberRolesMap, memberGroupsMap, memberTagsMap, memberManagers,
  loading, error, expandedId, overviewFields,
  getFieldValue, getFieldValueAsString, getMemberType, getMemberGroups, getColumnValues,
  toggleExpand,
} = useMemberData()

const activeTab = ref('ALL')

const tabStates = ref<Record<string, TabFilterState>>({
  ALL: emptyTabState(),
  TRIAL: emptyTabState(),
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
  { key: 'TRIAL', label: t('membersList.tabTrial') },
  { key: 'MEMBER', label: t('membersList.tabMember') },
  { key: 'GUARDIAN', label: t('membersList.tabMemberManager') },
  { key: 'TEAM', label: t('membersList.tabTeam') },
  { key: 'MANAGER', label: t('membersList.tabManager') },
])

const { savedFilters, loadSavedFilters, saveCurrentFilter, applyFilter, deleteFilter, clearFilters } =
  useSavedFilters(tabStates, activeTab)

const extraColumnIds = ref<Set<number>>(new Set())
const hiddenColumnIds = ref<Set<number>>(new Set())

const tabScopedFields = computed(() => {
  const scopeForTab: Record<string, string[]> = {
    ALL: [StationUserType.TRIAL, StationUserType.MEMBER, StationUserType.GUARDIAN, StationUserType.TEAM, StationUserType.MANAGER],
    [StationUserType.TRIAL]: [StationUserType.TRIAL],
    [StationUserType.MEMBER]: [StationUserType.MEMBER],
    [StationUserType.GUARDIAN]: [StationUserType.GUARDIAN],
    [StationUserType.TEAM]: [StationUserType.TEAM],
    [StationUserType.MANAGER]: [StationUserType.MANAGER],
  }
  const scopes = scopeForTab[activeTab.value] ?? []
  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return scopes.includes(f.scope ?? StationUserType.MEMBER)
  })
})

const tabOverviewFields = computed(() => tabScopedFields.value.filter(f => parseFieldConfig(f.config).overview))
const tabNonOverviewFields = computed(() => tabScopedFields.value.filter(f => !parseFieldConfig(f.config).overview))

const visibleColumns = computed(() => {
  const overview = tabOverviewFields.value.filter(f => !hiddenColumnIds.value.has(f.id))
  const extra = tabNonOverviewFields.value.filter(f => extraColumnIds.value.has(f.id))
  return [...overview, ...extra]
})

function toggleColumn(fieldId: number) {
  const isOverview = tabOverviewFields.value.some(f => f.id === fieldId)
  if (isOverview) {
    const next = new Set(hiddenColumnIds.value)
    if (next.has(fieldId)) next.delete(fieldId); else next.add(fieldId)
    hiddenColumnIds.value = next
  } else {
    const next = new Set(extraColumnIds.value)
    if (next.has(fieldId)) next.delete(fieldId); else next.add(fieldId)
    extraColumnIds.value = next
  }
}

const {
  onFilter: onMemberFilter,
  applyFilter: applyMemberFilter,
} = useMemberFilter(
    () => members.value,
    () => memberGroupsMap.value,
    () => memberTagsMap.value,
    () => allGroups.value,
    () => allTags.value,
)

const filteredMembers = computed(() => {
  let list = activeTab.value === 'ALL' ? members.value : members.value.filter(m => getMemberType(m.id) === activeTab.value)
  list = applyMemberFilter(list)

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

const {
  exportMode, selectedIds, showExportModal,
  toggleExportMode, toggleSelect, toggleSelectAll, openExportModal, performExport,
} = useExport(filteredMembers, fields, getMemberGroups, getFieldValueAsString)

function navigateToDetail(member: StationMember, event: Event) {
  event.stopPropagation()
  router.push({ name: 'members-detail', params: { id: member.id } })
}

function navigateToEdit(member: StationMember, event: Event) {
  event.stopPropagation()
  router.push({ name: 'members-edit', params: { id: member.id } })
}

const resendTarget = ref<StationMember | null>(null)
const resending = ref(false)
const resendError = ref('')
const resendSuccess = ref('')

function openResendSetup(member: StationMember, event: Event) {
  event.stopPropagation()
  resendTarget.value = member
  resendError.value = ''
}

async function confirmResendSetup() {
  if (!resendTarget.value) return
  resending.value = true
  resendError.value = ''
  try {
    await stationMembers.resendSetupMail(resendTarget.value.id)
    resendSuccess.value = t('membersList.resendSuccess')
    resendTarget.value = null
  } catch (e) {
    const data = (e as {response?: {data?: {title?: string; message?: string}}})?.response?.data
    resendError.value = data?.title ?? data?.message ?? t('common.error')
  } finally {
    resending.value = false
  }
}

onMounted(() => {
  loadSavedFilters()
})
</script>

<template>
  <ViewContent
      :title="t('pages.members-list.title')"
      :subtitle="t('pages.members-list.subtitle')"
  >
    <ListViewBody
      v-model:active-tab="activeTab"
      v-model:filter-text="filterText"
      v-model:show-export-modal="showExportModal"
      :loading="loading"
      :error="error"
      :tabs="tabs"
      :saved-filters="savedFilters"
      :tab-overview-fields="tabOverviewFields"
      :tab-non-overview-fields="tabNonOverviewFields"
      :tab-scoped-fields="tabScopedFields"
      :extra-column-ids="extraColumnIds"
      :hidden-column-ids="hiddenColumnIds"
      :export-mode="exportMode"
      :selected-ids="selectedIds"
      :can-export="canExport"
      :can-edit="canEdit"
      :groups="allGroups"
      :tags="allTags"
      :filtered-members="filteredMembers"
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
      @clear-filters="clearFilters"
      @apply-filter="applyFilter"
      @delete-filter="deleteFilter"
      @save-filter="saveCurrentFilter"
      @toggle-column="toggleColumn"
      @toggle-export="toggleExportMode"
      @export-continue="openExportModal"
      @filter="onMemberFilter"
      @toggle-sort="toggleSort"
      @apply-column-filter="applyColumnFilter"
      @toggle-expand="toggleExpand"
      @navigate-detail="navigateToDetail"
      @navigate-edit="navigateToEdit"
      @resend-setup="openResendSetup"
      @toggle-select="toggleSelect"
      @toggle-select-all="toggleSelectAll"
      @export="performExport"
    />

    <Modal v-if="resendTarget" model-value @update:model-value="(v) => { if (!v) resendTarget = null }">
      <div class="space-y-4">
        <p>{{ t('membersList.resendConfirm', {name: resendTarget?.name ?? ''}) }}</p>
        <Alert v-if="resendError" variant="error">{{ resendError }}</Alert>
        <div class="flex justify-end gap-3">
          <SecondaryButton :disabled="resending" @click="resendTarget = null">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :icon="['fas', 'paper-plane']" :disabled="resending" @click="confirmResendSetup">
            {{ t('membersList.resendAction') }}
          </PrimaryButton>
        </div>
      </div>
    </Modal>

    <Alert v-if="resendSuccess" variant="success" class="mt-4">
      {{ resendSuccess }}
      <a class="ml-2 underline cursor-pointer" @click="resendSuccess = ''">{{ t('common.close') }}</a>
    </Alert>
  </ViewContent>
</template>
