/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MemberTableHeader from './MemberTableHeader.vue'
import MemberTableBody from './MemberTableBody.vue'
import type {ProfileField, StationMember} from '@/api/types'

type ColumnKey = 'name' | 'groups' | 'tags' | number

defineProps<{
  members: StationMember[]
  visibleColumns: ProfileField[]
  expandedId: number | null
  allSelected: boolean
  sortIcon: (column: 'name' | number) => string
  hasActiveFilter: (key: ColumnKey) => boolean
  openFilterModal: (key: ColumnKey, label: string) => void
  getMemberGroups: (memberId: number) => string[]
  getMemberTags: (memberId: number) => string[]
  isFieldApplicable: (memberId: number, field: ProfileField) => boolean
  getFieldValue: (memberId: number, fieldId: number) => unknown
  getApplicableOverviewFields: (memberId: number) => ProfileField[]
  getManagers: (memberId: number) => StationMember[]
  managerName: (mgr: StationMember) => string
  exportMode?: boolean
  selectedIds?: Set<number>
  canEdit?: boolean
}>()

const emit = defineEmits<{
  toggleSort: [column: 'name' | number]
  toggleSelectAll: []
  rowClick: [member: StationMember]
  toggleSelect: [memberId: number]
  navigateDetail: [member: StationMember, event: Event]
  navigateEdit: [member: StationMember, event: Event]
}>()
</script>

<template>
  <div class="overflow-x-auto">
    <table class="w-full text-sm">
      <MemberTableHeader
          :visible-columns="visibleColumns"
          :all-selected="allSelected"
          :sort-icon="sortIcon"
          :has-active-filter="hasActiveFilter"
          :open-filter-modal="openFilterModal"
          :export-mode="exportMode"
          @toggle-sort="(c) => emit('toggleSort', c)"
          @toggle-select-all="emit('toggleSelectAll')"
      />
      <MemberTableBody
          :members="members"
          :visible-columns="visibleColumns"
          :expanded-id="expandedId"
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
          @row-click="(m) => emit('rowClick', m)"
          @toggle-select="(id) => emit('toggleSelect', id)"
          @navigate-detail="(m, e) => emit('navigateDetail', m, e)"
          @navigate-edit="(m, e) => emit('navigateEdit', m, e)"
      />
    </table>
  </div>
</template>
