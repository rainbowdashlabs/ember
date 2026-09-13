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
import EmptyState from '@/components/feedback/EmptyState.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MovementFilterBar from './movementqueueview/MovementFilterBar.vue'
import MovementQueueRow from './movementqueueview/MovementQueueRow.vue'
import MovementAckModal from './movementqueueview/MovementAckModal.vue'
import MovementCorrectModal from './movementqueueview/MovementCorrectModal.vue'
import MovementWizard from './movementwizard/MovementWizard.vue'
import {useMovementQueue} from './movementqueueview/useMovementQueue'
import {inventory, movements, profileFields} from '@/api'
import type {Movement} from '@/api/movements'
import type {ProfileField} from '@/api/profileFields'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {saveBlob} from '@/util/downloadAuthed'

/**
 * Every Vorgang the station has, in the order of whose turn it is.
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

/** The sheet for the shelf, over the rows the filters leave standing. */
async function exportPdf() {
  exporting.value = true
  exportError.value = ''
  try {
    const blob = await movements.exportPdf(queue.visible.value.map(row => row.id), [])
    saveBlob(blob, 'vorgaenge.pdf')
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
        <div class="flex flex-wrap items-center justify-end gap-2">
          <SecondaryButton v-if="canManage" :disabled="exporting" :icon="['fas', 'file-export']"
                           data-testid="movement-export" @click="exportPdf">
            {{ exporting ? t('common.loading') : t('movements.queue.export') }}
          </SecondaryButton>
          <PrimaryButton :icon="['fas', 'plus']" data-testid="movement-create" @click="showWizard = true">
            {{ t('movements.queue.create') }}
          </PrimaryButton>
        </div>

        <Alert v-if="exportError" variant="error">{{ exportError }}</Alert>

        <MovementFilterBar
            v-model:inventory-ids="queue.inventoryIds.value"
            v-model:purposes="queue.purposes.value"
            v-model:search="queue.search.value"
            v-model:states="queue.states.value"
            v-model:turns="queue.turns.value"
            :inventories="queue.inventories.value"
            :show-sort="true"
            :sort-key="queue.sortKey.value"
            @sort="queue.selectSort"
        />

        <EmptyState v-if="rows.length === 0">{{ t('movements.queue.empty') }}</EmptyState>
        <EmptyState v-else-if="queue.visible.value.length === 0">{{ t('movements.queue.noMatch') }}</EmptyState>

        <MovementQueueRow
            v-for="movement in queue.visible.value"
            :key="movement.id"
            :can-correct="isManager"
            :movement="movement"
            :show-member="canManage"
            @acknowledge="acknowledging = movement.id"
            @correct="correcting = movement.id"
            @open="openDetail(movement)"
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
