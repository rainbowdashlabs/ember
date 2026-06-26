/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {
  ColumnEntry,
  GdprDeletionContext,
  GdprExportContext,
  TableEntry,
  TrackingStatusName,
  TransferContext,
} from '@/api/dataTracking'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import TableColumnList from './TableColumnList.vue'
import TableSchemaSection from './TableSchemaSection.vue'
import TableTransferSection from './TableTransferSection.vue'
import TableExportSection from './TableExportSection.vue'
import TableDeletionSection from './TableDeletionSection.vue'

defineProps<{
  entry: TableEntry
  columns: ColumnEntry[]
  stationTransfer: TransferContext
  gdprExport: GdprExportContext
  gdprDeletion: GdprDeletionContext
  statuses: TrackingStatusName[]
  strategies: readonly string[]
  columnOptions: { value: string; label: string; group?: string }[]
  error: string
  save: () => Promise<void>
}>()

const emit = defineEmits<{
  close: []
  verifyAll: []
  addStrategy: []
  removeStrategy: [index: number]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="p-4 space-y-6">
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <TableColumnList
        :columns="columns"
        :foreign-keys="entry.foreignKeys"
        @verify-all="emit('verifyAll')"
    />

    <TableSchemaSection :entry="entry"/>

    <TableTransferSection
        :transfer="stationTransfer"
        :statuses="statuses"
        :column-options="columnOptions"
    />

    <TableExportSection
        :export-ctx="gdprExport"
        :statuses="statuses"
        :column-options="columnOptions"
    />

    <TableDeletionSection
        :deletion="gdprDeletion"
        :statuses="statuses"
        :strategies="strategies"
        :columns="columns"
        @add-strategy="emit('addStrategy')"
        @remove-strategy="(i) => emit('removeStrategy', i)"
    />

    <div class="sticky bottom-0 bg-(--bg) border-t border-(--border) -mx-4 px-4 py-3 flex items-center gap-2 justify-end">
      <SecondaryButton @click="emit('close')">{{ t('common.cancel') }}</SecondaryButton>
      <SaveButton :action="save"/>
    </div>
  </div>
</template>
