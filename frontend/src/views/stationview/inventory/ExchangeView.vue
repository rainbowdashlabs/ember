/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import ExportFieldPicker from '@/components/export/ExportFieldPicker.vue'
import ExchangeToolbar from './exchangeview/ExchangeToolbar.vue'
import ExchangeListView from './exchangeview/ExchangeListView.vue'
import ExchangeModals from './exchangeview/ExchangeModals.vue'
import {ExchangeStatus, type ExchangeRequestEntry, type ExchangeStatusName} from '@/api/exchanges'
import {InventoryTypes, type Inventory, type InventoryItem} from '@/api/inventory'
import type {ProfileField} from '@/api/profileFields'
import type {StationMember} from '@/api/types'
import type { ManagedMember } from '@/api/managedMembers'
import { exchanges, inventory, profileFields, stationMembers, managedMembers } from '@/api'
import { useSession } from '@/composables/useSession'
import { useStations } from '@/composables/useStations'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useExport } from '@/composables/useExport'
import { saveBlob } from '@/util/downloadAuthed'

const { t } = useI18n()
const { canManageExchanges, isGuardian, sessionInfo, loaded } = useSession()
const { activeStation } = useStations()

const requests = ref<ExchangeRequestEntry[]>([])
const inventories = ref<Inventory[]>([])
const members = ref<StationMember[]>([])
const managed = ref<ManagedMember[]>([])
const membersWithItems = ref<Set<number>>(new Set())
const allFields = ref<ProfileField[]>([])

const {
  exportMode, selectedIds: selectedForExport, selectedColumns: selectedExportFields,
  selectedRows: selectedRequests, columnOptions: exportFieldOptions,
  startExport, cancelExport, toggleRow, toggleAllRows, toggleColumn,
} = useExport({
  rows: () => requests.value,
  rowId: r => r.id,
  columns: () => allFields.value.map(f => ({key: String(f.id), label: f.name ?? ''})),
  selectAllRows: true,
})

const showMemberColumn = computed(() => canManageExchanges() || isGuardian())
const showCreateModal = ref(false)

const membersWithItemsList = computed(() =>
  members.value.filter(m => membersWithItems.value.has(m.id))
)
const managedWithItemsList = computed(() =>
  managed.value.filter(m => membersWithItems.value.has(m.id))
)

const internalFlow: ExchangeStatusName[] = [ExchangeStatus.ANNOUNCED, ExchangeStatus.RECEIVED, ExchangeStatus.DONE]
const externalFlow: ExchangeStatusName[] = [ExchangeStatus.ANNOUNCED, ExchangeStatus.RECEIVED, ExchangeStatus.SHIPPED, ExchangeStatus.ARRIVED, ExchangeStatus.DONE]

function getFlow(inventoryType: string): ExchangeStatusName[] {
  return inventoryType === InventoryTypes.INTERNAL ? internalFlow : externalFlow
}

function nextStatuses(current: ExchangeStatusName, inventoryType: string): ExchangeStatusName[] {
  const flow = getFlow(inventoryType)
  const idx = flow.indexOf(current)
  if (idx < 0 || idx >= flow.length - 1) return []
  return flow.slice(idx + 1)
}

function nextStatusesFor(request: ExchangeRequestEntry): ExchangeStatusName[] {
  return nextStatuses(request.status, request.inventoryType)
}

const updatingId = ref<number | null>(null)
const availableItems = ref<InventoryItem[]>([])

const showLogModal = ref(false)
const logExchangeId = ref<number | null>(null)

const {loading, error, reload} = useAsyncLoader(async () => {
  const stationId = activeStation.value?.stationId
  const [r, inv, m, mgd] = await Promise.all([
    exchanges.listExchanges(),
    canManageExchanges() ? inventory.listInventories() : Promise.resolve([]),
    stationId && canManageExchanges() ? stationMembers.listMembers() : Promise.resolve([]),
    isGuardian() ? managedMembers.listManaged() : Promise.resolve([]),
  ])
  requests.value = r
  inventories.value = inv
  members.value = m
  managed.value = mgd
  const withItems = new Set<number>()
  if (inv.length > 0) {
    const itemArrays = await Promise.all(inv.map(i => inventory.listItems(i.id)))
    for (const items of itemArrays) {
      for (const item of items) {
        if (item.assignedTo && !item.lostAt) withItems.add(item.assignedTo)
      }
    }
  } else if (mgd.length > 0) {
    const selfId = sessionInfo.value?.member?.id
    const idsToCheck = selfId ? [selfId, ...mgd.map(m => m.id)] : mgd.map(m => m.id)
    const results = await Promise.all(idsToCheck.map(async id => {
      try {
        const items = id === selfId ? await inventory.myItems() : await managedMembers.getMemberInventory(id)
        return items.some(i => !i.lostAt) ? id : null
      } catch { return null }
    }))
    for (const id of results) { if (id) withItems.add(id) }
  }
  membersWithItems.value = withItems
}, {autoLoad: false})

loading.value = true
if (loaded.value) reload()

async function startStatusUpdate(request: ExchangeRequestEntry) {
  updatingId.value = request.id
  try {
    const allItems = await inventory.listItems(request.inventoryId)
    availableItems.value = allItems.filter(i => !i.assignedTo && !i.lostAt)
  } catch { availableItems.value = [] }
}

async function onStatusUpdated() {
  updatingId.value = null
  requests.value = await exchanges.listExchanges()
}

async function deleteRequest(id: number) {
  error.value = ''
  try {
    await exchanges.deleteExchange(id)
    requests.value = await exchanges.listExchanges()
  } catch { error.value = t('common.error') }
}

function openLog(id: number) {
  logExchangeId.value = id
  showLogModal.value = true
}

async function enterExportMode() {
  try { allFields.value = await profileFields.listFields() } catch { allFields.value = [] }
  startExport()
}

const {running: exporting, error: exportError, run: exportSelected} = useAsyncAction(async () => {
  const blob = await exchanges.exportPdf(
      selectedRequests.value.map(r => r.id),
      [...selectedExportFields.value].map(Number),
  )
  saveBlob(blob, 'exchange-requests.pdf')
  cancelExport()
}, {formatError: () => t('common.error')})

async function onCreated() {
  requests.value = await exchanges.listExchanges()
}

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
})
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-exchanges.title')"
      :subtitle="t('pages.inventory-exchanges.subtitle')"
  >
    <div class="space-y-6">
      <ExchangeToolbar
        :export-mode="exportMode"
        :exporting="exporting"
        :selected-count="selectedForExport.size"
        :can-export="canManageExchanges() && requests.length > 0"
        @export="exportSelected"
        @cancel-export="cancelExport"
        @enter-export="enterExportMode"
        @create="showCreateModal = true"
      />

      <Alert v-if="error || exportError" variant="error">{{ error || exportError }}</Alert>

      <ExportFieldPicker
        v-if="exportMode && exportFieldOptions.length > 0"
        boxed
        layout="inline"
        :label="t('exchanges.exportFieldsHint')"
        :options="exportFieldOptions"
        :selected="selectedExportFields"
        @toggle="toggleColumn"
      />

      <AsyncSection
        :empty="requests.length === 0"
        :empty-message="t('exchanges.empty')"
        :loading="loading"
      >
        <ExchangeListView
          :requests="requests" :show-member-column="showMemberColumn" :can-manage-exchanges="canManageExchanges()"
          :export-mode="exportMode" :selected-for-export="selectedForExport" :updating-id="updatingId"
          :available-items="availableItems" :next-statuses-for="nextStatusesFor"
          @toggle-select-all="toggleAllRows" @toggle-export="toggleRow" @open-log="openLog"
          @start-update="startStatusUpdate" @delete="deleteRequest" @status-done="onStatusUpdated"
          @status-cancel="updatingId = null" @status-error="(msg) => error = msg"
        />
      </AsyncSection>

      <ExchangeModals
        v-model:show-create="showCreateModal" v-model:show-log="showLogModal"
        :requests="requests" :members-with-items="membersWithItems"
        :members-with-items-list="membersWithItemsList" :managed-with-items-list="managedWithItemsList"
        :managed="managed" :log-exchange-id="logExchangeId"
        @created="onCreated" @error="(msg) => error = msg"
      />
    </div>
  </ViewContent>
</template>
