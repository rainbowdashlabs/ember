/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {dataTracking} from '@/api'
import type {
  ColumnEntry,
  DeletionStrategy,
  GdprDeletionContext,
  GdprExportContext,
  TableEntry,
  TrackingStatusName,
  TransferContext,
} from '@/api/dataTracking'
import {TrackingStatus} from '@/api/dataTracking'
import TableDetailHeader from './TableDetailHeader.vue'
import TableDetailBody from './TableDetailBody.vue'

const props = defineProps<{
  name: string
  entry: TableEntry
}>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'updated', name: string, entry: TableEntry): void
}>()

const {t} = useI18n()

const stationTransfer = ref<TransferContext>({...props.entry.stationTransfer})
const gdprExport = ref<GdprExportContext>({...props.entry.gdprExport})
const gdprDeletion = ref<GdprDeletionContext>({...props.entry.gdprDeletion})
const columns = ref<ColumnEntry[]>(props.entry.columns.map(c => ({...c})))
const error = ref('')

watch(
    () => props.entry,
    e => {
      stationTransfer.value = {...e.stationTransfer}
      gdprExport.value = {...e.gdprExport}
      gdprDeletion.value = {...e.gdprDeletion}
      columns.value = e.columns.map(c => ({...c}))
      error.value = ''
    },
)

const columnOptions = computed(() =>
    columns.value.map(c => ({value: c.name, label: c.name, group: c.type})),
)

const statuses: TrackingStatusName[] = [
  TrackingStatus.TRACKED,
  TrackingStatus.IGNORED,
  TrackingStatus.UNVERIFIED,
]

const STRATEGIES = [
  'CASCADE',
  'DELETE_EXPLICIT',
  'ANONYMIZE',
  'NULL',
  'RETAIN',
  'RETAIN_UNLINKED',
  'NOT_APPLICABLE',
] as const

function ensureDeletionStrategies(): DeletionStrategy[] {
  if (!gdprDeletion.value.strategies) gdprDeletion.value.strategies = []
  return gdprDeletion.value.strategies
}

function addDeletionStrategy() {
  const arr = ensureDeletionStrategies()
  const firstColumn = columns.value[0]?.name ?? ''
  arr.push({column: firstColumn, strategy: 'NULL', reason: '', legalBasis: null})
}

function removeDeletionStrategy(index: number) {
  const arr = ensureDeletionStrategies()
  arr.splice(index, 1)
}

async function save() {
  error.value = ''
  try {
    const overrides: Record<string, boolean> = {}
    for (let i = 0; i < columns.value.length; i++) {
      const before = props.entry.columns[i]
      const after = columns.value[i]
      if (before && before.verified !== after.verified) overrides[after.name] = after.verified
    }
    const updated = await dataTracking.updateDataTrackingTable(props.name, {
      columnVerified: Object.keys(overrides).length > 0 ? overrides : undefined,
      stationTransfer: stationTransfer.value,
      gdprExport: gdprExport.value,
      gdprDeletion: gdprDeletion.value,
    })
    emit('updated', props.name, updated)
    emit('close')
  } catch (e) {
    error.value = (e as Error).message || t('common.error')
    throw e
  }
}

async function verifyAll() {
  error.value = ''
  try {
    const updated = await dataTracking.verifyAllColumns(props.name)
    columns.value = updated.columns.map(c => ({...c}))
    emit('updated', props.name, updated)
  } catch (e) {
    error.value = (e as Error).message || t('common.error')
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex">
    <div class="flex-1 bg-black/40" @click="emit('close')"/>
    <div class="w-full max-w-2xl bg-(--bg) border-l border-(--border) shadow-2xl overflow-y-auto">
      <TableDetailHeader :name="name" :entry="entry" @close="emit('close')"/>
      <TableDetailBody
          :entry="entry"
          :columns="columns"
          :station-transfer="stationTransfer"
          :gdpr-export="gdprExport"
          :gdpr-deletion="gdprDeletion"
          :statuses="statuses"
          :strategies="STRATEGIES"
          :column-options="columnOptions"
          :error="error"
          :save="save"
          @close="emit('close')"
          @verify-all="verifyAll"
          @add-strategy="addDeletionStrategy"
          @remove-strategy="removeDeletionStrategy"
      />
    </div>
  </div>
</template>
