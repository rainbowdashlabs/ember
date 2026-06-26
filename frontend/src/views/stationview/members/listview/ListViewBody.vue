/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import LoadedContent from './LoadedContent.vue'
import ExportModal from './ExportModal.vue'
import type { StationMember, ProfileField, MemberGroup, UserTag, PermissionGrant } from '@/api/types'

defineProps<{
  loading: boolean
  error: string
  activeTab: string
  filterText: string
  tabs: { key: string; label: string }[]
  savedFilters: { id: number; name: string }[]
  tabOverviewFields: ProfileField[]
  tabNonOverviewFields: ProfileField[]
  tabScopedFields: ProfileField[]
  extraColumnIds: Set<number>
  hiddenColumnIds: Set<number>
  exportMode: boolean
  selectedIds: Set<number>
  canExport: boolean
  canEdit: boolean
  groups: MemberGroup[]
  tags: UserTag[]
  filteredMembers: StationMember[]
  visibleColumns: ProfileField[]
  expandedId: number | null
  sortColumn: 'name' | number
  sortAsc: boolean
  columnMultiFilters: Map<'name' | 'groups' | 'tags' | number, Set<string>>
  columnEmptyFilters: Set<'name' | 'groups' | 'tags' | number>
  memberGroupsMap: Map<number, MemberGroup[]>
  memberTagsMap: Map<number, UserTag[]>
  memberRolesMap: Map<number, PermissionGrant[]>
  memberManagers: Map<number, StationMember[]>
  allMembers: StationMember[]
  overviewFields: ProfileField[]
  getFieldValue: (memberId: number, fieldId: number) => unknown
  showExportModal: boolean
}>()

defineEmits<{
  (e: 'update:activeTab', value: string): void
  (e: 'update:filterText', value: string): void
  (e: 'update:showExportModal', value: boolean): void
  (e: 'clear-filters'): void
  (e: 'apply-filter', filterId: number): void
  (e: 'delete-filter', filterId: number): void
  (e: 'save-filter', name: string): void
  (e: 'toggle-column', fieldId: number): void
  (e: 'toggle-export'): void
  (e: 'export-continue'): void
  (e: 'filter', filter: unknown): void
  (e: 'toggle-sort', column: 'name' | number): void
  (e: 'apply-column-filter', key: 'name' | 'groups' | 'tags' | number, selected: Set<string>, includeEmpty: boolean): void
  (e: 'toggle-expand', id: number): void
  (e: 'navigate-detail', member: StationMember, event: Event): void
  (e: 'navigate-edit', member: StationMember, event: Event): void
  (e: 'toggle-select', id: number): void
  (e: 'toggle-select-all'): void
  (e: 'export', config: unknown): void
}>()
</script>

<template>
  <Spinner v-if="loading" size="lg" />
  <Alert v-if="error" variant="error">{{ error }}</Alert>

  <LoadedContent
    v-if="!loading"
    :active-tab="activeTab"
    :filter-text="filterText"
    :tabs="tabs"
    :saved-filters="savedFilters"
    :tab-overview-fields="tabOverviewFields"
    :tab-non-overview-fields="tabNonOverviewFields"
    :extra-column-ids="extraColumnIds"
    :hidden-column-ids="hiddenColumnIds"
    :export-mode="exportMode"
    :selected-count="selectedIds.size"
    :can-export="canExport"
    :groups="groups"
    :tags="tags"
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
    :all-members="allMembers"
    :overview-fields="overviewFields"
    :get-field-value="getFieldValue"
    :selected-ids="selectedIds"
    :can-edit="canEdit"
    @update:active-tab="$emit('update:activeTab', $event)"
    @update:filter-text="$emit('update:filterText', $event)"
    @clear-filters="$emit('clear-filters')"
    @apply-filter="$emit('apply-filter', $event)"
    @delete-filter="$emit('delete-filter', $event)"
    @save-filter="$emit('save-filter', $event)"
    @toggle-column="$emit('toggle-column', $event)"
    @toggle-export="$emit('toggle-export')"
    @export-continue="$emit('export-continue')"
    @filter="$emit('filter', $event)"
    @toggle-sort="$emit('toggle-sort', $event)"
    @apply-column-filter="(k, s, e) => $emit('apply-column-filter', k, s, e)"
    @toggle-expand="$emit('toggle-expand', $event)"
    @navigate-detail="(m, e) => $emit('navigate-detail', m, e)"
    @navigate-edit="(m, e) => $emit('navigate-edit', m, e)"
    @toggle-select="$emit('toggle-select', $event)"
    @toggle-select-all="$emit('toggle-select-all')"
  />

  <ExportModal
    :model-value="showExportModal"
    :available-fields="tabScopedFields"
    :selected-count="selectedIds.size"
    @update:model-value="$emit('update:showExportModal', $event)"
    @export="$emit('export', $event)"
  />
</template>
