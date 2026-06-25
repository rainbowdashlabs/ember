/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TabBar from '@/components/navigation/TabBar.vue'
import MemberFilterBar from './FilterBar.vue'
import MemberTable from './Table.vue'
import type { StationMember, ProfileField, MemberGroup, UserTag } from '@/api/types'

interface SavedFilter {
  id?: number
  name: string
  tab: string
  textFilters: Record<string, string>
  multiFilters: Record<string, string[]>
}

defineProps<{
  activeTab: string
  tabs: { key: string; label: string }[]
  filterText: string
  savedFilters: SavedFilter[]
  tabOverviewFields: ProfileField[]
  tabNonOverviewFields: ProfileField[]
  extraColumnIds: Set<number>
  hiddenColumnIds: Set<number>
  exportMode: boolean
  selectedCount: number
  canExport: boolean
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
  memberRolesMap: Map<number, string[]>
  memberManagers: Map<number, StationMember[]>
  allMembers: StationMember[]
  overviewFields: ProfileField[]
  getFieldValue: (memberId: number, fieldId: number) => unknown
  selectedIds: Set<number>
  canEdit: boolean
}>()

defineEmits<{
  (e: 'update:activeTab', value: string): void
  (e: 'update:filterText', value: string): void
  (e: 'clear-filters'): void
  (e: 'apply-filter', filter: SavedFilter): void
  (e: 'delete-filter', id: number): void
  (e: 'save-filter', name: string): void
  (e: 'toggle-column', fieldId: number): void
  (e: 'toggle-export'): void
  (e: 'export-continue'): void
  (e: 'filter', data: unknown): void
  (e: 'toggle-sort', column: 'name' | number): void
  (e: 'apply-column-filter', key: 'name' | 'groups' | 'tags' | number, selected: Set<string>, includeEmpty: boolean): void
  (e: 'toggle-expand', id: number): void
  (e: 'navigate-detail', member: StationMember, event: Event): void
  (e: 'navigate-edit', member: StationMember, event: Event): void
  (e: 'toggle-select', id: number): void
  (e: 'toggle-select-all'): void
}>()
</script>

<template>
  <TabBar
    :model-value="activeTab"
    :tabs="tabs"
    @update:model-value="$emit('update:activeTab', $event)"
  />

  <MemberFilterBar
    :filter-text="filterText"
    :saved-filters="savedFilters"
    :overview-fields="tabOverviewFields"
    :non-overview-fields="tabNonOverviewFields"
    :extra-column-ids="extraColumnIds"
    :hidden-column-ids="hiddenColumnIds"
    :export-mode="exportMode"
    :selected-count="selectedCount"
    :can-export="canExport"
    :groups="groups"
    :tags="tags"
    @update:filter-text="$emit('update:filterText', $event)"
    @clear-filters="$emit('clear-filters')"
    @apply-filter="$emit('apply-filter', $event)"
    @delete-filter="$emit('delete-filter', $event)"
    @save-filter="$emit('save-filter', $event)"
    @toggle-column="$emit('toggle-column', $event)"
    @toggle-export="$emit('toggle-export')"
    @export-continue="$emit('export-continue')"
    @filter="$emit('filter', $event)"
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
    :all-members="allMembers"
    :overview-fields="overviewFields"
    :get-field-value="getFieldValue"
    :export-mode="exportMode"
    :selected-ids="selectedIds"
    :can-edit="canEdit"
    @toggle-sort="$emit('toggle-sort', $event)"
    @apply-column-filter="(k, s, e) => $emit('apply-column-filter', k, s, e)"
    @toggle-expand="$emit('toggle-expand', $event)"
    @navigate-detail="(m, e) => $emit('navigate-detail', m, e)"
    @navigate-edit="(m, e) => $emit('navigate-edit', m, e)"
    @toggle-select="$emit('toggle-select', $event)"
    @toggle-select-all="$emit('toggle-select-all')"
  />
</template>
