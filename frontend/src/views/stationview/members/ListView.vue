/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import ListViewBody from './listview/ListViewBody.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {StationPermission, type StationMember} from '@/api/types'
import { STATION_MEMBER_SOURCE } from './listview/useMemberData'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useSession } from '@/composables/useSession'
import { useMemberListConfig, type MemberListPort } from './listview/useMemberListConfig'
import { stationMembers } from '@/api'

const { t } = useI18n()
const { hasPermission } = useSession()

/** A station lists its own roll and reaches its own member screens. */
const port: MemberListPort = {
  source: STATION_MEMBER_SOURCE,
  routes: {detail: 'members-detail', edit: 'members-edit'},
  canExport: computed(() => hasPermission(StationPermission.MEMBER_EXPORT)),
  canEdit: computed(() => hasPermission(StationPermission.MEMBER_EDIT)),
  exportFileName: 'mitglieder',
}

const {
  members, allGroups, allTags, memberRolesMap, memberGroupsMap, memberTagsMap, memberManagers,
  loading, error, expandedId, overviewFields, getFieldValue, toggleExpand,
  activeTab, tabs, filterText, columnMultiFilters, columnEmptyFilters,
  sortKey, sortDirection, extraColumnIds, hiddenColumnIds,
  tabOverviewFields, tabNonOverviewFields, visibleColumns, toggleColumn, applyColumnFilter,
  savedFilters, saveCurrentFilter, applyFilter, deleteFilter, clearFilters,
  onMemberFilter, sortedMembers, toggleSort,
  exportMode, selectedIds, showExportModal, selectedColumns, columnOptions,
  toggleExportMode, toggleRow, toggleAllRows, toggleExportColumn,
  selectColumns, openExportModal, performExport,
  canExport, canEdit, navigateToDetail, navigateToEdit,
} = useMemberListConfig(port)

const resendTarget = ref<StationMember | null>(null)
const resendSuccess = ref('')

const {
  running: resending,
  error: resendError,
  run: confirmResendSetup,
  clearError: clearResendError,
} = useAsyncAction(async () => {
  if (!resendTarget.value) return
  await stationMembers.resendSetupMail(resendTarget.value.id)
  resendSuccess.value = t('membersList.resendSuccess')
  resendTarget.value = null
}, {
  formatError: e => {
    const data = (e as {response?: {data?: {title?: string; message?: string}}})?.response?.data
    return data?.title ?? data?.message ?? t('common.error')
  },
})

function openResendSetup(member: StationMember, event: Event) {
  event.stopPropagation()
  resendTarget.value = member
  clearResendError()
}
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
      :export-columns="columnOptions"
      :selected-export-columns="selectedColumns"
      :extra-column-ids="extraColumnIds"
      :hidden-column-ids="hiddenColumnIds"
      :export-mode="exportMode"
      :selected-ids="selectedIds"
      :can-export="canExport"
      :can-edit="canEdit"
      :groups="allGroups"
      :tags="allTags"
      :members="sortedMembers"
      :visible-columns="visibleColumns"
      :expanded-id="expandedId"
      :sort-key="sortKey"
      :sort-direction="sortDirection"
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
      @toggle-select="toggleRow"
      @toggle-select-all="toggleAllRows"
      @toggle-export-column="toggleExportColumn"
      @select-export-columns="selectColumns"
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
