/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ListViewBody from './ListViewBody.vue'
import type {useMemberListConfig} from './useMemberListConfig'
import type {StationMember} from '@/api/types'

/**
 * The member table, wired to a configuration.
 *
 * <p>{@link ListViewBody} takes thirty props, and every screen that shows a member list would
 * otherwise hand it the same thirty. This is that binding, written once, so a screen says which
 * configuration it is drawing and nothing else.
 */
const props = defineProps<{
  config: ReturnType<typeof useMemberListConfig>
  /** The rows to draw, when the screen narrows them further than the configuration does. */
  members?: StationMember[]
}>()

const emit = defineEmits<{
  resendSetup: [member: StationMember, event: Event]
}>()

const c = props.config
</script>

<template>
  <ListViewBody
    v-model:active-tab="c.activeTab.value"
    v-model:filter-text="c.filterText.value"
    v-model:show-export-modal="c.showExportModal.value"
    :loading="c.loading.value"
    :error="c.error.value"
    :tabs="c.tabs.value"
    :saved-filters="c.savedFilters.value"
    :tab-overview-fields="c.tabOverviewFields.value"
    :tab-non-overview-fields="c.tabNonOverviewFields.value"
    :export-columns="c.columnOptions.value"
    :selected-export-columns="c.selectedColumns.value"
    :extra-column-ids="c.extraColumnIds.value"
    :hidden-column-ids="c.hiddenColumnIds.value"
    :export-mode="c.exportMode.value"
    :selected-ids="c.selectedIds.value"
    :can-export="c.canExport.value"
    :can-edit="c.canEdit.value"
    :groups="c.allGroups.value"
    :tags="c.allTags.value"
    :members="props.members ?? c.sortedMembers.value"
    :visible-columns="c.visibleColumns.value"
    :expanded-id="c.expandedId.value"
    :sort-key="c.sortKey.value"
    :sort-direction="c.sortDirection.value"
    :column-multi-filters="c.columnMultiFilters.value"
    :column-empty-filters="c.columnEmptyFilters.value"
    :member-groups-map="c.memberGroupsMap.value"
    :member-tags-map="c.memberTagsMap.value"
    :member-roles-map="c.memberRolesMap.value"
    :member-managers="c.memberManagers.value"
    :all-members="c.members.value"
    :overview-fields="c.overviewFields.value"
    :get-field-value="c.getFieldValue"
    @clear-filters="c.clearFilters"
    @apply-filter="c.applyFilter"
    @delete-filter="c.deleteFilter"
    @save-filter="c.saveCurrentFilter"
    @toggle-column="c.toggleColumn"
    @toggle-export="c.toggleExportMode"
    @export-continue="c.openExportModal"
    @filter="c.onMemberFilter"
    @toggle-sort="c.toggleSort"
    @apply-column-filter="c.applyColumnFilter"
    @toggle-expand="c.toggleExpand"
    @navigate-detail="c.navigateToDetail"
    @navigate-edit="c.navigateToEdit"
    @resend-setup="(m: StationMember, e: Event) => emit('resendSetup', m, e)"
    @toggle-select="c.toggleRow"
    @toggle-select-all="c.toggleAllRows"
    @toggle-export-column="c.toggleExportColumn"
    @select-export-columns="c.selectColumns"
    @export="c.performExport"
  />
</template>
