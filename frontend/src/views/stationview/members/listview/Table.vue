/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ColumnFilterModal from '@/components/table/ColumnFilterModal.vue'
import MemberCardMobile from './MemberCardMobile.vue'
import MemberDesktopTable from './MemberDesktopTable.vue'
import type {ProfileField} from '@/api/profileFields'
import {StationUserType, type StationMember} from '@/api/types'
import {useBreakpoint} from '@/composables/useBreakpoint'
import {sortIconFor, type SortDirection} from '@/composables/useSortable'
import type {MemberSortKey} from './useSavedFilters'
import EmptyState from '@/components/feedback/EmptyState.vue'

const {isMobile} = useBreakpoint()
const {t} = useI18n()

const props = defineProps<{
  members: StationMember[]
  visibleColumns: ProfileField[]
  expandedId: number | null
  sortKey: MemberSortKey
  sortDirection: SortDirection
  columnMultiFilters: Map<'name' | 'groups' | 'tags' | number, Set<string>>
  columnEmptyFilters: Set<'name' | 'groups' | 'tags' | number>
  memberGroupsMap: Map<number, string[]>
  memberTagsMap: Map<number, string[]>
  memberRolesMap: Map<number, string[]>
  memberManagers: Map<number, StationMember[]>
  allMembers: StationMember[]
  overviewFields: ProfileField[]
  getFieldValue: (memberId: number, fieldId: number) => unknown
  exportMode?: boolean
  selectedIds?: Set<number>
  canEdit?: boolean
}>()

const emit = defineEmits<{
  toggleSort: [column: MemberSortKey]
  applyColumnFilter: [key: 'name' | 'groups' | 'tags' | number, selected: Set<string>, includeEmpty: boolean]
  toggleExpand: [member: StationMember]
  navigateDetail: [member: StationMember, event: Event]
  navigateEdit: [member: StationMember, event: Event]
  resendSetup: [member: StationMember, event: Event]
  toggleSelect: [memberId: number]
  toggleSelectAll: []
}>()

const allSelected = computed(() => {
  if (!props.selectedIds || props.members.length === 0) return false
  return props.members.every(m => props.selectedIds!.has(m.id))
})

const filterModalOpen = ref(false)
const filterModalColumn = ref<'name' | 'groups' | 'tags' | number>('name')
const filterModalLabel = ref('')
const filterModalValues = ref<string[]>([])
const filterModalSelected = ref<Set<string>>(new Set())
const filterModalIncludeEmpty = ref(false)

function sortIcon(column: MemberSortKey): string {
  return sortIconFor(props.sortKey === column, props.sortDirection)
}

function hasActiveFilter(key: 'name' | 'groups' | 'tags' | number): boolean {
  const multi = props.columnMultiFilters.get(key)
  if (multi && multi.size > 0) return true
  return props.columnEmptyFilters.has(key)
}

function openFilterModal(key: 'name' | 'groups' | 'tags' | number, label: string) {
  filterModalColumn.value = key
  filterModalLabel.value = label
  filterModalValues.value = getUniqueValuesForColumn(key)
  filterModalSelected.value = new Set(props.columnMultiFilters.get(key) ?? [])
  filterModalIncludeEmpty.value = props.columnEmptyFilters.has(key)
  filterModalOpen.value = true
}

function getUniqueValuesForColumn(key: 'name' | 'groups' | 'tags' | number): string[] {
  const vals = new Set<string>()
  for (const m of props.allMembers) {
    if (key === 'name') {
      const name = memberDisplayName(m)
      if (name && name !== `#${m.id}`) vals.add(name)
    } else if (key === 'groups') {
      for (const g of getMemberGroups(m.id)) vals.add(g)
    } else if (key === 'tags') {
      for (const tag of getMemberTags(m.id)) vals.add(tag)
    } else {
      const v = props.getFieldValue(m.id, key)
      if (v != null && v !== '') vals.add(String(v))
    }
  }
  return [...vals].sort()
}

function onFilterApply(selected: Set<string>, includeEmpty: boolean) {
  emit('applyColumnFilter', filterModalColumn.value, selected, includeEmpty)
}

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function getMemberGroups(memberId: number): string[] {
  return props.memberGroupsMap.get(memberId) ?? []
}

function getMemberTags(memberId: number): string[] {
  return props.memberTagsMap.get(memberId) ?? []
}

function getScopeForUserType(roles: string[]): string {
  if (roles.includes(StationUserType.MANAGER)) return 'MANAGER'
  if (roles.includes(StationUserType.TEAM)) return 'TEAM'
  if (roles.includes(StationUserType.GUARDIAN)) return 'GUARDIAN'
  return 'MEMBER'
}

function isFieldApplicable(memberId: number, field: ProfileField): boolean {
  const roles = props.memberRolesMap.get(memberId) ?? []
  return getScopeForUserType(roles) === field.scope
}

function getApplicableOverviewFields(memberId: number): ProfileField[] {
  const roles = props.memberRolesMap.get(memberId) ?? []
  const scope = getScopeForUserType(roles)
  return props.overviewFields.filter(f => f.scope === scope)
}

function getManagers(memberId: number): StationMember[] {
  return props.memberManagers.get(memberId) ?? []
}

function managerName(mgr: StationMember): string {
  const m = props.allMembers.find(mem => mem.id === mgr.id)
  return m ? memberDisplayName(m) : `#${mgr.id}`
}

function onRowClick(member: StationMember) {
  if (props.exportMode) {
    emit('toggleSelect', member.id)
  } else {
    emit('toggleExpand', member)
  }
}
</script>

<template>
  <div v-if="isMobile" class="space-y-3">
    <EmptyState v-if="members.length === 0">{{ t('membersList.empty') }}</EmptyState>
    <MemberCardMobile
        v-for="member in members"
        :key="member.id"
        :member="member"
        :visible-columns="visibleColumns"
        :member-groups="getMemberGroups(member.id)"
        :member-tags="getMemberTags(member.id)"
        :is-field-applicable="(f) => isFieldApplicable(member.id, f)"
        :get-field-value="(id) => getFieldValue(member.id, id)"
        :export-mode="exportMode"
        :selected="selectedIds?.has(member.id)"
        :can-edit="canEdit"
        @click="onRowClick(member)"
        @toggle-select="emit('toggleSelect', member.id)"
        @navigate-detail="(e) => emit('navigateDetail', member, e)"
        @navigate-edit="(e) => emit('navigateEdit', member, e)"
    />
  </div>

  <template v-else>
    <MemberDesktopTable
        :members="members"
        :visible-columns="visibleColumns"
        :expanded-id="expandedId"
        :all-selected="allSelected"
        :sort-icon="sortIcon"
        :has-active-filter="hasActiveFilter"
        :open-filter-modal="openFilterModal"
        :get-member-groups="getMemberGroups"
        :get-member-tags="getMemberTags"
        :is-field-applicable="isFieldApplicable"
        :get-field-value="getFieldValue"
        :get-applicable-overview-fields="getApplicableOverviewFields"
        :get-managers="getManagers"
        :manager-name="managerName"
        :export-mode="exportMode"
        :selected-ids="selectedIds"
        :can-edit="canEdit"
        @toggle-sort="(c) => emit('toggleSort', c)"
        @toggle-select-all="emit('toggleSelectAll')"
        @row-click="onRowClick"
        @toggle-select="(id) => emit('toggleSelect', id)"
        @navigate-detail="(m, e) => emit('navigateDetail', m, e)"
        @navigate-edit="(m, e) => emit('navigateEdit', m, e)"
        @resend-setup="(m, e) => emit('resendSetup', m, e)"
    />
    <EmptyState v-if="members.length === 0">{{ t('membersList.empty') }}</EmptyState>
  </template>

  <ColumnFilterModal
      v-model="filterModalOpen"
      :column-label="filterModalLabel"
      :values="filterModalValues"
      :selected-values="filterModalSelected"
      :include-empty="filterModalIncludeEmpty"
      @apply="onFilterApply"
      @close="filterModalOpen = false"
  />
</template>
