/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import Alert from '@/components/feedback/Alert.vue'
import MovementQueueToolbar from './movementqueueview/MovementQueueToolbar.vue'
import MovementFilterBar from './movementqueueview/MovementFilterBar.vue'
import MovementQueueList from './movementqueueview/MovementQueueList.vue'
import MovementAckModal from './movementqueueview/MovementAckModal.vue'
import MovementCorrectModal from './movementqueueview/MovementCorrectModal.vue'
import MovementWizard from './movementwizard/MovementWizard.vue'
import {useMovementQueue} from './movementqueueview/useMovementQueue'
import {MovementColumn, useMovementColumns} from './movementqueueview/movementColumns'
import {queueOrder} from './movementqueueview/movementFilter'
import {movements, profileFields} from '@/api'
import type {Movement} from '@/api/movements'
import type {ProfileField} from '@/api/profileFields'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useDataTable} from '@/composables/useDataTable'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {saveBlob} from '@/util/downloadAuthed'
import {useExport} from '@/composables/useExport'

/**
 * Every movement the station has, in the order of whose turn it is.
 *
 * <p>The rows somebody here can act on stand at the top, because that is what a queue is read for. A
 * row says what it is about, who it is with and the step it stands on, and where the step is ours it
 * carries the button that acknowledges it. Nothing on a row is a status: there is none.
 */
const {t} = useI18n()
const router = useRouter()
const routes = useInventoryRoutes()
const {hasPermission} = useSession()

const rows = ref<Movement[]>([])
const fields = ref<ProfileField[]>([])

const canManage = computed(() => hasPermission(StationPermission.INVENTORY_MOVEMENTS))
const isManager = computed(() => hasPermission(StationPermission.INVENTORY_MANAGER))

const queue = useMovementQueue(() => rows.value)

/** Who a movement is with is shown only to somebody working the whole queue. */
const allColumns = useMovementColumns()
const columns = computed(() => allColumns.value.filter(column => canManage.value || column.key !== MovementColumn.MEMBER))

const table = useDataTable<Movement>({
  id: 'inventory-movements',
  rows: () => queue.matching.value,
  columns,
  rowKey: movement => movement.id,
  fallbackSort: queueOrder,
})

const acknowledging = ref<number | null>(null)
const correcting = ref<number | null>(null)
const showWizard = ref(false)
const exporting = ref(false)
const exportError = ref('')

const {loading, error, reload} = useAsyncLoader(async () => {
  const [loaded, fieldRows] = await Promise.all([
    movements.listMovements(),
    canManage.value ? profileFields.listFields() : Promise.resolve([]),
  ])
  rows.value = loaded
  fields.value = fieldRows
})

/**
 * The sheet for the shelf: the rows that are ticked, with whichever profile fields are wanted beside
 * the names.
 *
 * <p>Pressing export opens the ticks rather than downloading at once. What the filters leave standing
 * is a starting point and not the answer: a sheet to walk a shelf with is usually a handful of rows
 * out of the list, and everything ticked to begin with makes that a matter of unticking.
 */
const exportFlow = useExport<Movement>({
  rows: () => table.rows,
  rowId: row => row.id,
  columns: () => fields.value.map(field => ({key: String(field.id), label: field.name ?? String(field.id)})),
  selectAllRows: true,
})

async function downloadPdf() {
  exporting.value = true
  exportError.value = ''
  try {
    const blob = await movements.exportPdf(
        exportFlow.selectedRows.value.map(row => row.id),
        [...exportFlow.selectedColumns.value].map(Number),
    )
    saveBlob(blob, 'movements.pdf')
    exportFlow.cancelExport()
  } catch {
    exportError.value = t('common.error')
  } finally {
    exporting.value = false
  }
}

function openDetail(movement: Movement) {
  if (!routes.movement) return
  void router.push({name: routes.movement, params: {id: String(movement.id)}})
}

function afterChange() {
  acknowledging.value = null
  correcting.value = null
  void reload()
}

</script>

<template>
  <ViewContent :subtitle="t('movements.queue.subtitle')" :title="t('movements.queue.title')">
    <AsyncSection :error="error" :loading="loading">
      <div class="space-y-4">
        <MovementQueueToolbar
            :all-picked="exportFlow.allRowsSelected.value"
            :busy="exporting"
            :can-export="canManage"
            :field-options="exportFlow.columnOptions.value"
            :picked-count="exportFlow.selectedRows.value.length"
            :picked-fields="exportFlow.selectedColumns.value"
            :picking="exportFlow.exportMode.value"
            @cancel="exportFlow.cancelExport"
            @create="showWizard = true"
            @download="downloadPdf"
            @start="exportFlow.startExport"
            @toggle-all="exportFlow.toggleAllRows"
            @toggle-field="exportFlow.toggleColumn"
        />

        <Alert v-if="exportError" variant="error">{{ exportError }}</Alert>

        <MovementFilterBar
            v-model:inventory-ids="queue.inventoryIds.value"
            v-model:purposes="queue.purposes.value"
            v-model:search="queue.search.value"
            v-model:states="queue.states.value"
            v-model:turns="queue.turns.value"
            :inventories="queue.inventories.value"
        />

        <MovementQueueList
            :can-correct="isManager"
            :picked-ids="exportFlow.selectedIds.value"
            :picking="exportFlow.exportMode.value"
            :table="table"
            :total="rows.length"
            @acknowledge="movement => acknowledging = movement.id"
            @correct="movement => correcting = movement.id"
            @open="openDetail"
            @pick="movement => exportFlow.toggleRow(movement.id)"
        />
      </div>
    </AsyncSection>

    <MovementAckModal
        v-if="acknowledging !== null"
        :can-force="isManager"
        :model-value="true"
        :movement-id="acknowledging"
        @done="afterChange"
        @update:model-value="open => { if (!open) acknowledging = null }"
    />
    <MovementCorrectModal
        v-if="correcting !== null"
        :model-value="true"
        :movement-id="correcting"
        @done="afterChange"
        @update:model-value="open => { if (!open) correcting = null }"
    />
    <MovementWizard v-model="showWizard" @started="afterChange"/>
  </ViewContent>
</template>
