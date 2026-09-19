/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import {dataTracking, demo} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import type {
  DataTracking,
  DataTrackingSummary,
  TableEntry,
  TableUpdatePayload,
  TrackingStatusName,
} from '@/api/dataTracking'
import ColumnPickerButton from '@/components/table/ColumnPickerButton.vue'
import TableDetailDrawer from './datatrackingview/TableDetailDrawer.vue'
import TrackingTable from './datatrackingview/TrackingTable.vue'
import {useTrackingTable} from './datatrackingview/trackingtable/useTrackingTable'
import SummaryCards from './datatrackingview/SummaryCards.vue'
import StatusBreakdownGrid from './datatrackingview/StatusBreakdownGrid.vue'
import DanglingRefAudit from './datatrackingview/DanglingRefAudit.vue'
import {findDanglingMemberRefs} from './datatrackingview/danglingMemberRefs'
import TableFilterBar from './datatrackingview/TableFilterBar.vue'
import BatchToolbar from './datatrackingview/BatchToolbar.vue'

const {t} = useI18n()

const tracking = ref<DataTracking | null>(null)
const summary = ref<DataTrackingSummary | null>(null)
const loading = ref(true)
const error = ref('')
const selectedTable = ref<string | null>(null)

const selectedForBatch = ref<Set<string>>(new Set())
const batchContext = ref<'stationTransfer' | 'gdprExport' | 'gdprDeletion'>('stationTransfer')
const batchStatus = ref<TrackingStatusName>('UNVERIFIED')
const batchError = ref('')

/**
 * Whether the backend is a dev instance, which is what decides this page rather than how the
 * frontend happened to be built: everything on it comes from routes that only a dev instance
 * registers, and a production build talking to one can show them perfectly well.
 */
const isDev = ref(false)

const {table, filterContext, filterStatus, needsReviewCount} = useTrackingTable(tracking)

const danglingMemberRefs = computed(() => findDanglingMemberRefs(tracking.value))

const verifiedPct = computed(() => {
  if (!summary.value || summary.value.totalColumns === 0) return 0
  return Math.round((summary.value.verifiedColumns / summary.value.totalColumns) * 100)
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [tk, sm] = await Promise.all([
      dataTracking.getDataTracking(),
      dataTracking.getDataTrackingSummary(),
    ])
    tracking.value = tk
    summary.value = sm
  } catch (e) {
    error.value = (e as Error).message || t('common.error')
  } finally {
    loading.value = false
  }
}

function selectedEntry(): TableEntry | null {
  if (!selectedTable.value || !tracking.value) return null
  return tracking.value.tables[selectedTable.value] ?? null
}

/** Reads the totals again after a change, keeping the ones shown where that fails. */
async function refreshSummary() {
  summary.value = await dataTracking.getDataTrackingSummary().catch(() => summary.value)
}

async function onTableUpdated(name: string, entry: TableEntry) {
  if (!tracking.value) return
  tracking.value = {
    ...tracking.value,
    tables: {...tracking.value.tables, [name]: entry},
  }
  await refreshSummary()
}

function toggleBatchSelection(name: string) {
  const next = new Set(selectedForBatch.value)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  selectedForBatch.value = next
}

function selectAllFiltered() {
  const next = new Set(selectedForBatch.value)
  for (const row of table.rows) next.add(row.name)
  selectedForBatch.value = next
}

function clearBatchSelection() {
  selectedForBatch.value = new Set()
}

/**
 * Applies {@link batchStatus} to {@link batchContext} on every table in {@link selectedForBatch}.
 * Calls the existing per-table update endpoint sequentially; failures are surfaced as a single
 * banner so partial progress is visible.
 */
const {running: batchSaving, run: applyBatch} = useAsyncAction(async () => {
  if (!tracking.value || selectedForBatch.value.size === 0) return
  batchError.value = ''
  const failures: string[] = []
  const successUpdates: Record<string, TableEntry> = {}

  for (const name of selectedForBatch.value) {
    const existing = tracking.value.tables[name]
    if (!existing) continue
    try {
      const payload: TableUpdatePayload = {}
      if (batchContext.value === 'stationTransfer') {
        payload.stationTransfer = {...existing.stationTransfer, status: batchStatus.value}
      } else if (batchContext.value === 'gdprExport') {
        payload.gdprExport = {...existing.gdprExport, status: batchStatus.value}
      } else {
        payload.gdprDeletion = {...existing.gdprDeletion, status: batchStatus.value}
      }
      const updated = await dataTracking.updateDataTrackingTable(name, payload)
      successUpdates[name] = updated
    } catch (e) {
      failures.push(`${name}: ${(e as Error).message ?? 'unknown error'}`)
    }
  }

  if (Object.keys(successUpdates).length > 0) {
    tracking.value = {
      ...tracking.value,
      tables: {...tracking.value.tables, ...successUpdates},
    }
    await refreshSummary()
  }

  if (failures.length > 0) {
    batchError.value = `${failures.length} update(s) failed:\n${failures.join('\n')}`
  } else {
    clearBatchSelection()
  }
})

onMounted(async () => {
  isDev.value = await demo.getDemoStatus().then(status => status.dev).catch(() => false)
  if (isDev.value) await loadData()
})
</script>

<template>
  <ViewContent v-if="isDev" :title="t('pages.admin-data-tracking.title')" :subtitle="t('pages.admin-data-tracking.subtitle')">
    <div class="flex flex-wrap items-center justify-between gap-2 mb-4">
      <span class="text-sm text-(--text-muted)">{{ t('adminDataTracking.devOnlyNotice') }}</span>
    </div>

    <Alert v-if="error" class="mb-4" variant="error">{{ error }}</Alert>

    <Spinner v-if="loading"/>

    <template v-else-if="summary">
      <SummaryCards
          :summary="summary"
          :needs-review-count="needsReviewCount"
          :verified-pct="verifiedPct"
          :schema-hash="tracking?.schemaHash"/>
      <StatusBreakdownGrid :summary="summary" class="mb-6"/>
      <DanglingRefAudit :refs="danglingMemberRefs"/>
      <SectionHeader>{{ t('adminDataTracking.tables') }}</SectionHeader>
      <TableFilterBar
          v-model:search="table.search"
          v-model:filter-context="filterContext"
          v-model:filter-status="filterStatus">
        <ColumnPickerButton :options="table.pickerOptions" @toggle="table.toggleColumn"/>
      </TableFilterBar>
      <BatchToolbar
          :selected-count="selectedForBatch.size"
          v-model:batch-context="batchContext"
          v-model:batch-status="batchStatus"
          :batch-saving="batchSaving"
          @apply="applyBatch"
          @select-all="selectAllFiltered"
          @clear="clearBatchSelection"/>
      <Alert v-if="batchError" variant="error" class="mb-3 whitespace-pre-line">{{ batchError }}</Alert>
      <TrackingTable
          :selected="selectedForBatch"
          :table="table"
          :tracking="tracking"
          @open="selectedTable = $event"
          @toggle-batch="toggleBatchSelection"/>
    </template>

    <TableDetailDrawer
        v-if="selectedTable && selectedEntry()"
        :name="selectedTable"
        :entry="selectedEntry()!"
        @close="selectedTable = null"
        @updated="onTableUpdated"/>
  </ViewContent>

  <ViewContent v-else :title="t('pages.admin-data-tracking.title')" :subtitle="t('pages.admin-data-tracking.subtitle')">
    <NeutralContainer>{{ t('adminDataTracking.devOnlyDisabled') }}</NeutralContainer>
  </ViewContent>
</template>
