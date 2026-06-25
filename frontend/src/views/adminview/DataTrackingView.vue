/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import {dataTracking} from '@/api'
import type {
  DataTracking,
  DataTrackingSummary,
  TableEntry,
  TableUpdatePayload,
  TrackingStatusName,
} from '@/api/dataTracking'
import TableDetailDrawer from './datatrackingview/TableDetailDrawer.vue'
import TableListRow from './datatrackingview/TableListRow.vue'
import SummaryCards from './datatrackingview/SummaryCards.vue'
import StatusBreakdownGrid from './datatrackingview/StatusBreakdownGrid.vue'
import DanglingRefAudit, {type DanglingRef} from './datatrackingview/DanglingRefAudit.vue'
import TableFilterBar from './datatrackingview/TableFilterBar.vue'
import BatchToolbar from './datatrackingview/BatchToolbar.vue'

const {t} = useI18n()

const tracking = ref<DataTracking | null>(null)
const summary = ref<DataTrackingSummary | null>(null)
const loading = ref(true)
const error = ref('')
const search = ref('')
const filterContext = ref<'all' | 'transfer' | 'gdprExport' | 'gdprDeletion'>('all')
const filterStatus = ref<'all' | 'TRACKED' | 'IGNORED' | 'UNVERIFIED' | 'NEEDS_REVIEW'>('all')
const selectedTable = ref<string | null>(null)

const selectedForBatch = ref<Set<string>>(new Set())
const batchContext = ref<'stationTransfer' | 'gdprExport' | 'gdprDeletion'>('stationTransfer')
const batchStatus = ref<TrackingStatusName>('UNVERIFIED')
const batchSaving = ref(false)
const batchError = ref('')

const isDev = import.meta.env.DEV

interface Row {
  name: string
  entry: TableEntry
  hasUnverifiedColumns: boolean
  needsReview: boolean
  rowKey: string
}

const rows = computed<Row[]>(() => {
  if (!tracking.value) return []
  return Object.entries(tracking.value.tables).map(([name, entry]) => {
    const hasUnverifiedColumns = entry.columns.some(c => !c.verified)
    const needsReview =
        entry.stationTransfer.status === 'UNVERIFIED' ||
        entry.gdprExport.status === 'UNVERIFIED' ||
        entry.gdprDeletion.status === 'UNVERIFIED' ||
        hasUnverifiedColumns
    return {
      name,
      entry,
      hasUnverifiedColumns,
      needsReview,
      rowKey: `${name}-${entry.tableHash}`,
    }
  })
})

function matchesSearch(r: Row, q: string): boolean {
  if (!q) return true
  if (r.name.toLowerCase().includes(q)) return true
  if (r.entry.description && r.entry.description.toLowerCase().includes(q)) return true
  return r.entry.columns.some(c =>
      c.name.toLowerCase().includes(q) || (c.description ?? '').toLowerCase().includes(q),
  )
}

/**
 * Returns every TRACKED gdprExport identity column whose declared column has no FK to
 * the station_member table — these are at-risk data-integrity links because deleting a member
 * won't clean them up via FK CASCADE. The station_member self-PK is filtered out (it IS the
 * member table).
 */
const danglingMemberRefs = computed<DanglingRef[]>(() => {
  if (!tracking.value) return []
  const out: DanglingRef[] = []
  for (const [name, entry] of Object.entries(tracking.value.tables)) {
    if (entry.gdprExport?.status !== 'TRACKED') continue
    for (const ic of entry.gdprExport.identityColumns ?? []) {
      if (ic.type !== 'MEMBER_ID') continue
      const colExists = entry.columns.some(c => c.name === ic.column)
      if (!colExists) {
        out.push({
          table: name,
          column: ic.column,
          identityType: ic.type,
          hint: 'column does not exist on the table',
        })
        continue
      }
      if (name === 'station_member') continue
      const fks = entry.foreignKeys ?? []
      const hasMemberFk = fks.some(fk => fk.column === ic.column && fk.refTable === 'station_member')
      if (!hasMemberFk) {
        const other = fks.find(fk => fk.column === ic.column)
        out.push({
          table: name,
          column: ic.column,
          identityType: ic.type,
          hint: other ? `FK points to ${other.refTable} instead of station_member` : 'no FK at all',
        })
      }
    }
  }
  return out.sort((a, b) => a.table.localeCompare(b.table) || a.column.localeCompare(b.column))
})

const filteredRows = computed<Row[]>(() => {
  const q = search.value.trim().toLowerCase()
  return rows.value.filter(r => {
    if (!matchesSearch(r, q)) return false
    if (filterStatus.value === 'NEEDS_REVIEW' && !r.needsReview) return false
    if (filterStatus.value !== 'all' && filterStatus.value !== 'NEEDS_REVIEW') {
      const ctxes = filterContext.value === 'all'
          ? [r.entry.stationTransfer.status, r.entry.gdprExport.status, r.entry.gdprDeletion.status]
          : [statusOf(r.entry)]
      if (!ctxes.includes(filterStatus.value)) return false
    }
    return true
  }).sort((a, b) => a.name.localeCompare(b.name))
})

function statusOf(entry: TableEntry): string {
  if (filterContext.value === 'transfer') return entry.stationTransfer.status
  if (filterContext.value === 'gdprExport') return entry.gdprExport.status
  if (filterContext.value === 'gdprDeletion') return entry.gdprDeletion.status
  return entry.stationTransfer.status
}

const needsReviewCount = computed(() => rows.value.filter(r => r.needsReview).length)

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

async function onTableUpdated(name: string, entry: TableEntry) {
  if (!tracking.value) return
  tracking.value = {
    ...tracking.value,
    tables: {...tracking.value.tables, [name]: entry},
  }
  try {
    summary.value = await dataTracking.getDataTrackingSummary()
  } catch { /* ignore */ }
}

function toggleBatchSelection(name: string) {
  const next = new Set(selectedForBatch.value)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  selectedForBatch.value = next
}

function selectAllFiltered() {
  const next = new Set(selectedForBatch.value)
  for (const r of filteredRows.value) next.add(r.name)
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
async function applyBatch() {
  if (!tracking.value || selectedForBatch.value.size === 0) return
  batchSaving.value = true
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
    try {
      summary.value = await dataTracking.getDataTrackingSummary()
    } catch { /* ignore */ }
  }

  batchSaving.value = false
  if (failures.length > 0) {
    batchError.value = `${failures.length} update(s) failed:\n${failures.join('\n')}`
  } else {
    clearBatchSelection()
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent v-if="isDev">
    <div class="flex flex-wrap items-center justify-between gap-2 mb-4">
      <PageHeader class="!mb-0">
        <font-awesome-icon :icon="['fas', 'database']" class="mr-2"/>
        {{ t('adminDataTracking.title') }}
      </PageHeader>
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
      <StatusBreakdownGrid :summary="summary"/>
      <DanglingRefAudit :refs="danglingMemberRefs"/>
      <SectionHeader>{{ t('adminDataTracking.tables') }}</SectionHeader>
      <TableFilterBar
          v-model:search="search"
          v-model:filter-context="filterContext"
          v-model:filter-status="filterStatus"/>
      <BatchToolbar
          :selected-count="selectedForBatch.size"
          v-model:batch-context="batchContext"
          v-model:batch-status="batchStatus"
          :batch-saving="batchSaving"
          @apply="applyBatch"
          @select-all="selectAllFiltered"
          @clear="clearBatchSelection"/>
      <Alert v-if="batchError" variant="error" class="mb-3 whitespace-pre-line">{{ batchError }}</Alert>
      <div class="space-y-1">
        <TableListRow
            v-for="row in filteredRows"
            :key="row.rowKey"
            :row="row"
            :selected="selectedForBatch.has(row.name)"
            :search="search"
            :tracking="tracking"
            @open="selectedTable = row.name"
            @toggle-batch="toggleBatchSelection(row.name)"/>
        <div v-if="filteredRows.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('adminDataTracking.noMatches') }}
        </div>
      </div>
    </template>

    <TableDetailDrawer
        v-if="selectedTable && selectedEntry()"
        :name="selectedTable"
        :entry="selectedEntry()!"
        @close="selectedTable = null"
        @updated="onTableUpdated"/>
  </ViewContent>

  <ViewContent v-else>
    <PageHeader>{{ t('adminDataTracking.title') }}</PageHeader>
    <NeutralContainer>{{ t('adminDataTracking.devOnlyDisabled') }}</NeutralContainer>
  </ViewContent>
</template>
