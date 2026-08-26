/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import {inventory, inventoryContainers} from '@/api'
import type {ContainerDetail, ItemLastCheck, InventoryContainer} from '@/api/inventoryContainers'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useFlashMessage} from '@/composables/useFlashMessage'
import WalkBreadcrumb from './checkcontainerwalkview/WalkBreadcrumb.vue'
import WalkCompletedPanel from './checkcontainerwalkview/WalkCompletedPanel.vue'
import WalkDeepToggle from './checkcontainerwalkview/WalkDeepToggle.vue'
import WalkExpectedList from './checkcontainerwalkview/WalkExpectedList.vue'
import WalkExtraList from './checkcontainerwalkview/WalkExtraList.vue'
import WalkNavigator from './checkcontainerwalkview/WalkNavigator.vue'
import WalkScanPanel from './checkcontainerwalkview/WalkScanPanel.vue'
import {useWalkPlan} from './checkcontainerwalkview/useWalkPlan'
import {countWalkResults, toCheckItems} from './checkcontainerwalkview/walkResults'
import type {ExpectedRow, ExtraRow} from './checkcontainerwalkview/types'
import {apiErrorMessage} from '@/util/apiError'
import {reportCaughtError} from '@/util/devErrorReporter'

const routes = useInventoryRoutes()

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const containerId = computed(() => Number(route.params.id))
const detail = ref<ContainerDetail | null>(null)
const allContainers = ref<InventoryContainer[]>([])
const expectedRows = ref<ExpectedRow[]>([])
const extraRows = ref<ExtraRow[]>([])
const deep = ref(false)
const loading = ref(true)
const error = ref('')
const scanValue = ref('')
const finishedCheck = ref<unknown | null>(null)
const {message: scanFlash, flash: flashScan} = useFlashMessage()
const {message: scanError, flash: flashScanError} = useFlashMessage(3000)
const walkIdx = ref(0)

const {containerById, walkOrder, rowsFor, pathFor} = useWalkPlan({
  containers: allContainers,
  rows: expectedRows,
  root: computed(() => detail.value?.container ?? null),
  deep,
})

const hasWalk = computed(() => deep.value && walkOrder.value.length > 1)

const currentContainer = computed<InventoryContainer | null>(() => walkOrder.value[walkIdx.value] ?? null)

const currentRows = computed<ExpectedRow[]>(() => {
  if (!currentContainer.value) return expectedRows.value
  return rowsFor(currentContainer.value.id)
})

const emptyRowsMessage = computed(() => hasWalk.value
    ? t('inventory.checkContainer.walkEmptySubtree')
    : t('inventory.checkContainer.expectedEmpty'))

const counts = computed(() => countWalkResults(expectedRows.value, extraRows.value))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [d, items, lastResults, all] = await Promise.all([
      inventoryContainers.getContainer(containerId.value),
      inventoryContainers.listExpectedItemsInContainer(containerId.value, deep.value),
      inventoryContainers.listLastCheckResults(containerId.value, deep.value),
      inventoryContainers.listContainers(),
    ])
    detail.value = d
    allContainers.value = all
    const lastByItem = new Map<number, ItemLastCheck>()
    for (const r of lastResults) lastByItem.set(r.itemId, r)
    expectedRows.value = items.map(i => ({item: i, result: 'PENDING', lastCheck: lastByItem.get(i.id)}))
    extraRows.value = []
    walkIdx.value = 0
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('inventory.checkContainer.loadError')
  } finally {
    loading.value = false
  }
}

async function onCameraScan(value: string) {
  scanValue.value = normaliseScannedPayload(value)
  await handleScan()
}

async function handleScan() {
  const term = scanValue.value.trim()
  if (!term) return
  scanValue.value = ''
  const row = expectedRows.value.find(r => r.item.internalId === term)
  if (row) {
    row.result = 'CONFIRMED'
    const rowContainerId = row.item.containerId ?? null
    if (hasWalk.value && currentContainer.value && rowContainerId !== currentContainer.value.id) {
      const c = rowContainerId != null ? containerById.value.get(rowContainerId) : null
      const path = c ? pathFor(c) : ''
      flashScan(t('inventory.checkContainer.walkConfirmedElsewhere', {name: row.item.name ?? '', path}))
    }
    return
  }
  try {
    const item = await inventory.findByInternalId(term)
    if (item) {
      if (!extraRows.value.some(r => r.item.id === item.id)) {
        extraRows.value.push({item})
      }
      return
    }
  } catch (e) {
    reportCaughtError(e, 'container walk scan lookup')
  }
  flashScanError(t('inventory.checkContainer.scanNoMatch', {scan: term}), 'error')
}

function markConfirmed(row: ExpectedRow) {
  row.result = 'CONFIRMED'
}

function markMissing(row: ExpectedRow) {
  row.result = 'NOT_IN_POSSESSION'
}

function markLost(row: ExpectedRow) {
  row.result = 'LOST'
}

function reset(row: ExpectedRow) {
  row.result = 'PENDING'
}

function removeExtra(itemId: number) {
  extraRows.value = extraRows.value.filter(r => r.item.id !== itemId)
}

function nextContainer() {
  if (walkIdx.value < walkOrder.value.length - 1) walkIdx.value++
}

function prevContainer() {
  if (walkIdx.value > 0) walkIdx.value--
}

const {running: submitting, error: finishError, run: finishCheck} = useAsyncAction(async () => {
  if (!detail.value) return
  finishedCheck.value = await inventoryContainers.completeContainerCheck(containerId.value, {
    deep: deep.value,
    items: toCheckItems(expectedRows.value, extraRows.value),
  })
}, {formatError: (e) => apiErrorMessage(e) ?? t('inventory.checkContainer.completeError')})

const displayError = computed(() => scanError.value || finishError.value || error.value)

function backToOverview() {
  router.push({name: routes.checkContainerOverview})
}

const isLast = computed(() => walkIdx.value >= walkOrder.value.length - 1)

onMounted(load)
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-check-container-walk.title')"
      :subtitle="t('pages.inventory-check-container-walk.subtitle')"
  >
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else-if="detail">
      <WalkBreadcrumb :path-display="detail.pathDisplay" />

      <WalkDeepToggle v-model:deep="deep" @change="load" />

      <div v-if="displayError" class="mb-4">
        <Alert variant="error">{{ displayError }}</Alert>
      </div>

      <WalkCompletedPanel v-if="finishedCheck" @back="backToOverview" />

      <template v-else>
        <WalkScanPanel
            v-model:scan="scanValue"
            :flash="scanFlash"
            :counts="counts"
            @submit="handleScan"
            @decoded="onCameraScan"
        />

        <template v-if="hasWalk && currentContainer">
          <WalkNavigator
              :container="currentContainer"
              :path="pathFor(currentContainer)"
              :position="walkIdx + 1"
              :total="walkOrder.length"
              :is-last="isLast"
              @prev="prevContainer"
              @next="nextContainer"
          />
        </template>

        <WalkExpectedList
            :rows="currentRows"
            :empty-message="emptyRowsMessage"
            @confirm="markConfirmed"
            @missing="markMissing"
            @lost="markLost"
            @reset="reset"
        />

        <WalkExtraList
            v-if="extraRows.length > 0"
            :rows="extraRows"
            @remove="removeExtra"
        />

        <div class="flex justify-end gap-2">
          <SecondaryButton @click="backToOverview">{{ t('common.cancel') }}</SecondaryButton>
          <SuccessButton v-if="!hasWalk || isLast" :disabled="submitting" @click="finishCheck">
            <font-awesome-icon :icon="['fas', 'check-double']" class="mr-2" />
            {{ submitting ? t('common.saving') : t('inventory.checkContainer.finish') }}
          </SuccessButton>
        </div>
      </template>
    </template>
    <Alert v-else variant="error">{{ error }}</Alert>
  </ViewContent>
</template>
