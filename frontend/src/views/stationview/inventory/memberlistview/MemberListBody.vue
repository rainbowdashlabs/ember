/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ExportFieldPicker from '@/components/export/ExportFieldPicker.vue'
import MemberListFilters from './MemberListFilters.vue'
import MemberListTable from './MemberListTable.vue'
import type { Inventory, InventoryItem, MemberGroup, ProfileField, StationMember, UserTag } from '@/api/types'
import type { ExportFieldOption } from '@/composables/useExport'

const { t } = useI18n()

const props = defineProps<{
  showEmpty: boolean
  groups: MemberGroup[]
  tags: UserTag[]
  inventories: Inventory[]
  displayedInventories: Inventory[]
  visibleInventoryIds: Set<number>
  showName: boolean
  showInternalId: boolean
  showSize: boolean
  exportMode: boolean
  allFields: ProfileField[]
  selectedExportFields: Set<number>
  filteredMembers: StationMember[]
  selectedForExport: Set<number>
  isMobile: boolean
  memberItemMap: Map<number, Map<number, InventoryItem[]>>
  sizeMap: Map<number, string>
}>()

const emit = defineEmits<{
  (e: 'update:show-empty', value: boolean): void
  (e: 'update:show-name', value: boolean): void
  (e: 'update:show-internal-id', value: boolean): void
  (e: 'update:show-size', value: boolean): void
  (e: 'filter', filter: (members: StationMember[]) => StationMember[]): void
  (e: 'toggle-inventory', inventoryId: number): void
  (e: 'toggle-export-field', fieldId: number): void
  (e: 'go-to-member', memberId: number): void
  (e: 'toggle-export-selection', memberId: number): void
  (e: 'toggle-select-all'): void
}>()

const fieldOptions = computed((): ExportFieldOption<number>[] =>
  props.allFields.map(f => ({ key: f.id, label: f.name ?? '' })),
)
</script>

<template>
  <MemberListFilters
    :show-empty="showEmpty"
    :show-name="showName"
    :show-internal-id="showInternalId"
    :show-size="showSize"
    :groups="groups"
    :tags="tags"
    :inventories="inventories"
    :visible-inventory-ids="visibleInventoryIds"
    @update:show-empty="emit('update:show-empty', $event)"
    @update:show-name="emit('update:show-name', $event)"
    @update:show-internal-id="emit('update:show-internal-id', $event)"
    @update:show-size="emit('update:show-size', $event)"
    @filter="emit('filter', $event)"
    @toggle-inventory="emit('toggle-inventory', $event)"
  />

  <ExportFieldPicker
    v-if="exportMode && fieldOptions.length > 0"
    boxed
    layout="inline"
    :label="t('inventoryMembers.exportFieldsHint')"
    :options="fieldOptions"
    :selected="selectedExportFields"
    @toggle="emit('toggle-export-field', $event)"
  />

  <EmptyState v-if="filteredMembers.length === 0">{{ t('inventoryMembers.empty') }}</EmptyState>

  <MemberListTable
    v-if="filteredMembers.length > 0"
    :members="filteredMembers"
    :inventories="displayedInventories"
    :export-mode="exportMode"
    :selected-for-export="selectedForExport"
    :is-mobile="isMobile"
    :member-item-map="memberItemMap"
    :show-name="showName"
    :show-internal-id="showInternalId"
    :show-size="showSize"
    :size-map="sizeMap"
    @go-to-member="emit('go-to-member', $event)"
    @toggle-export-selection="emit('toggle-export-selection', $event)"
    @toggle-select-all="emit('toggle-select-all')"
  />

  <p v-if="filteredMembers.length > 0" class="text-xs text-(--text-muted)">
    {{ filteredMembers.length }} {{ t('inventoryMembers.memberCount') }}
  </p>
</template>
