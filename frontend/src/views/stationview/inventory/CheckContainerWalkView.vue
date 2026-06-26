/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {inventory, inventoryContainers} from '@/api'
import type {ContainerDetail, ContainerCheckItemResult, ItemLastCheck, InventoryContainer} from '@/api/inventoryContainers'
import type {InventoryItem} from '@/api/types'
import {formatDate} from '@/util/format'

interface ExpectedRow {
  item: InventoryItem
  result: 'PENDING' | 'CONFIRMED' | 'NOT_IN_POSSESSION' | 'LOST'
  lastCheck?: ItemLastCheck
}

interface ExtraRow {
  item: InventoryItem
}

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
const submitting = ref(false)
const finishedCheck = ref<unknown | null>(null)
const scanFlash = ref('')
const walkIdx = ref(0)

const containerById = computed(() => {
  const m = new Map<number, InventoryContainer>()
  for (const c of allContainers.value) m.set(c.id, c)
  return m
})

const childrenByParent = computed(() => {
  const m = new Map<number, InventoryContainer[]>()
  for (const c of allContainers.value) {
    if (c.parentId == null) continue
    const list = m.get(c.parentId) ?? []
    list.push(c)
    m.set(c.parentId, list)
  }
  for (const list of m.values()) list.sort((a, b) => a.name.localeCompare(b.name))
  return m
})

const expectedRowsByContainer = computed(() => {
  const m = new Map<number, ExpectedRow[]>()
  for (const row of expectedRows.value) {
    const cid = row.item.containerId ?? null
    if (cid == null) continue
    const list = m.get(cid) ?? []
    list.push(row)
    m.set(cid, list)
  }
  return m
})

const walkOrder = computed<InventoryContainer[]>(() => {
  if (!detail.value) return []
  const root = containerById.value.get(detail.value.container.id) ?? detail.value.container
  if (!deep.value) return [root]
  const out: InventoryContainer[] = []
  const stack: InventoryContainer[] = [root]
  while (stack.length > 0) {
    const node = stack.pop()!
    out.push(node)
    const children = childrenByParent.value.get(node.id) ?? []
    for (let i = children.length - 1; i >= 0; i--) stack.push(children[i])
  }
  return out.filter(c => (expectedRowsByContainer.value.get(c.id)?.length ?? 0) > 0)
})

const hasWalk = computed(() => deep.value && walkOrder.value.length > 1)

const currentContainer = computed<InventoryContainer | null>(() => walkOrder.value[walkIdx.value] ?? null)

const currentRows = computed<ExpectedRow[]>(() => {
  if (!currentContainer.value) return expectedRows.value
  return expectedRowsByContainer.value.get(currentContainer.value.id) ?? []
})

function pathFor(c: InventoryContainer): string {
  const segments: string[] = []
  let cursor: number | null | undefined = c.id
  const seen = new Set<number>()
  while (cursor != null && !seen.has(cursor)) {
    seen.add(cursor)
    const node = containerById.value.get(cursor)
    if (!node) break
    segments.unshift(node.name)
    cursor = node.parentId ?? null
  }
  return segments.join(' / ')
}

const counts = computed(() => {
  let confirmed = 0
  let missing = 0
  let pending = 0
  for (const row of expectedRows.value) {
    if (row.result === 'CONFIRMED') confirmed++
    else if (row.result === 'NOT_IN_POSSESSION' || row.result === 'LOST') missing++
    else pending++
  }
  return {confirmed, missing, pending, extra: extraRows.value.length}
})

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
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.checkContainer.loadError')
  } finally {
    loading.value = false
  }
}

async function reloadOnDeepChange() {
  await load()
}

async function onCameraScan(value: string) {
  scanValue.value = normaliseScannedPayload(value)
  await handleScan()
}

function flashScan(msg: string) {
  scanFlash.value = msg
  setTimeout(() => (scanFlash.value = ''), 2500)
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
  } catch {
    // fall through
  }
  error.value = t('inventory.checkContainer.scanNoMatch', {scan: term})
  setTimeout(() => (error.value = ''), 3000)
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

async function finishCheck() {
  if (!detail.value) return
  submitting.value = true
  error.value = ''
  try {
    const items: ContainerCheckItemResult[] = []
    for (const row of expectedRows.value) {
      const resolved = row.result === 'PENDING' ? 'NOT_IN_POSSESSION' : row.result
      items.push({
        itemId: row.item.id,
        inventoryId: row.item.inventoryId,
        result: resolved,
        note: '',
      })
    }
    for (const extra of extraRows.value) {
      items.push({
        itemId: extra.item.id,
        inventoryId: extra.item.inventoryId,
        result: 'EXTRA',
        note: '',
      })
    }
    finishedCheck.value = await inventoryContainers.completeContainerCheck(containerId.value, {
      deep: deep.value,
      items,
    })
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.checkContainer.completeError')
  } finally {
    submitting.value = false
  }
}

function backToOverview() {
  router.push({name: 'inventory-check-container-overview'})
}

const isLast = computed(() => walkIdx.value >= walkOrder.value.length - 1)

onMounted(load)
</script>

<template>
  <ViewContent>
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else-if="detail">
      <div class="flex items-center flex-wrap gap-2 mb-3 text-sm text-(--text-muted)">
        <router-link :to="{name: 'inventory-check-container-overview'}" class="hover:underline">
          {{ t('inventory.checkContainer.title') }}
        </router-link>
        <span>/</span>
        <span class="text-(--text)">{{ detail.pathDisplay }}</span>
      </div>

      <div class="flex items-center justify-end mb-4 gap-3 flex-wrap">
        <label class="flex items-center gap-2 text-sm">
          <ToggleInput v-model="deep" @update:model-value="reloadOnDeepChange" />
          <span>{{ t('inventory.checkContainer.deep') }}</span>
        </label>
      </div>

      <div v-if="error" class="mb-4">
        <Alert variant="error">{{ error }}</Alert>
      </div>

      <NeutralContainer v-if="finishedCheck" class="mb-4">
        <Alert variant="success">{{ t('inventory.checkContainer.completed') }}</Alert>
        <div class="flex gap-2 mt-3">
          <SecondaryButton @click="backToOverview">{{ t('inventory.checkContainer.backToOverview') }}</SecondaryButton>
        </div>
      </NeutralContainer>

      <template v-else>
        <NeutralContainer class="mb-4">
          <SubHeader class="mb-2">{{ t('inventory.checkContainer.scanTitle') }}</SubHeader>
          <div class="flex gap-2">
            <TextInput
                v-model="scanValue"
                :placeholder="t('inventory.checkContainer.scanPlaceholder')"
                @keydown.enter="handleScan"
                class="flex-1"
            />
            <ScanButton mode="continuous" @decoded="onCameraScan" />
          </div>
          <Alert v-if="scanFlash" variant="info" class="mt-3">{{ scanFlash }}</Alert>
          <div class="flex flex-wrap gap-2 text-sm mt-3">
            <SuccessBadge>{{ t('inventory.checkContainer.statusConfirmed') }}: {{ counts.confirmed }}</SuccessBadge>
            <InfoBadge>{{ t('inventory.checkContainer.statusPending') }}: {{ counts.pending }}</InfoBadge>
            <ErrorBadge>{{ t('inventory.checkContainer.statusMissing') }}: {{ counts.missing }}</ErrorBadge>
            <InfoBadge v-if="counts.extra > 0">{{ t('inventory.checkContainer.statusExtra') }}: {{ counts.extra }}</InfoBadge>
          </div>
        </NeutralContainer>

        <template v-if="hasWalk && currentContainer">
          <NeutralContainer class="mb-4">
            <div class="flex items-center justify-between gap-3 flex-wrap">
              <div>
                <SubHeader>{{ currentContainer.name }}</SubHeader>
                <div class="text-xs text-(--text-muted) mt-0.5">
                  <font-awesome-icon :icon="['fas', 'location-dot']" class="mr-1.5" />
                  {{ pathFor(currentContainer) }}
                </div>
                <div class="text-xs text-(--text-muted) mt-1">
                  {{ t('inventory.checkContainer.walkPosition', {current: walkIdx + 1, total: walkOrder.length}) }}
                </div>
              </div>
              <div class="flex gap-2">
                <SecondaryButton :disabled="walkIdx === 0" @click="prevContainer">
                  <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2" />
                  {{ t('inventory.checkContainer.walkPrev') }}
                </SecondaryButton>
                <PrimaryButton v-if="!isLast" @click="nextContainer">
                  {{ t('inventory.checkContainer.walkNext') }}
                  <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-2" />
                </PrimaryButton>
              </div>
            </div>
            <div v-if="isLast" class="mt-3 text-sm text-(--text-muted)">
              {{ t('inventory.checkContainer.walkFinishHint') }}
            </div>
          </NeutralContainer>
        </template>

        <SectionHeader>{{ t('inventory.checkContainer.expected') }}</SectionHeader>
        <NeutralContainer class="mb-4">
          <EmptyState
              v-if="currentRows.length === 0"
              :message="hasWalk
                  ? t('inventory.checkContainer.walkEmptySubtree')
                  : t('inventory.checkContainer.expectedEmpty')"
          />
          <ul v-else class="divide-y divide-(--bg-accent)">
            <li v-for="row in currentRows" :key="row.item.id" class="py-2 flex items-center gap-3">
              <span class="flex-1">
                <span class="font-medium">{{ row.item.name }}</span>
                <span v-if="row.item.internalId" class="text-xs text-(--text-muted) ml-2">{{ row.item.internalId }}</span>
                <span v-if="row.lastCheck" class="block text-xs text-(--text-muted) mt-0.5">
                  {{ t('inventory.checkContainer.lastChecked', {
                    date: formatDate(row.lastCheck.checkedAt),
                    by: row.lastCheck.checkerName || t('common.unknown'),
                  }) }}
                  —
                  {{ t(`inventory.checkContainer.previousResult.${row.lastCheck.result}`) }}
                </span>
                <span v-else class="block text-xs text-(--text-muted) mt-0.5">
                  {{ t('inventory.checkContainer.lastCheckedNever') }}
                </span>
              </span>
              <SuccessBadge v-if="row.result === 'CONFIRMED'">{{ t('inventory.checkContainer.statusConfirmed') }}</SuccessBadge>
              <ErrorBadge v-else-if="row.result === 'LOST'">{{ t('inventory.checkContainer.statusLost') }}</ErrorBadge>
              <ErrorBadge v-else-if="row.result === 'NOT_IN_POSSESSION'">{{ t('inventory.checkContainer.statusMissing') }}</ErrorBadge>
              <InfoBadge v-else>{{ t('inventory.checkContainer.statusPending') }}</InfoBadge>
              <div class="flex gap-1">
                <IconButton v-if="row.result === 'PENDING'" :icon="['fas', 'check']" :label="t('inventory.checkContainer.markConfirmed')" @click="row.result = 'CONFIRMED'" />
                <IconButton v-if="row.result !== 'NOT_IN_POSSESSION'" :icon="['fas', 'xmark']" :label="t('inventory.checkContainer.markMissing')" @click="markMissing(row)" />
                <IconButton v-if="row.result !== 'LOST'" :icon="['fas', 'triangle-exclamation']" :label="t('inventory.checkContainer.markLost')" @click="markLost(row)" />
                <IconButton v-if="row.result !== 'PENDING'" :icon="['fas', 'rotate-left']" :label="t('inventory.checkContainer.reset')" @click="reset(row)" />
              </div>
            </li>
          </ul>
        </NeutralContainer>

        <template v-if="extraRows.length > 0">
          <SectionHeader>{{ t('inventory.checkContainer.extra') }}</SectionHeader>
          <NeutralContainer class="mb-4">
            <ul class="divide-y divide-(--bg-accent)">
              <li v-for="row in extraRows" :key="row.item.id" class="py-2 flex items-center gap-3">
                <span class="flex-1">
                  <span class="font-medium">{{ row.item.name }}</span>
                  <span v-if="row.item.internalId" class="text-xs text-(--text-muted) ml-2">{{ row.item.internalId }}</span>
                </span>
                <InfoBadge>{{ t('inventory.checkContainer.statusExtra') }}</InfoBadge>
                <IconButton :icon="['fas', 'trash']" :label="t('inventory.checkContainer.removeExtra')" @click="removeExtra(row.item.id)" />
              </li>
            </ul>
          </NeutralContainer>
        </template>

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
