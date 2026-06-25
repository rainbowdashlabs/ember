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
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {inventory, inventoryContainers} from '@/api'
import type {ContainerDetail, ContainerCheckItemResult} from '@/api/inventoryContainers'
import type {InventoryItem} from '@/api/types'

interface ExpectedRow {
  item: InventoryItem
  result: 'PENDING' | 'CONFIRMED' | 'NOT_IN_POSSESSION' | 'LOST'
}

interface ExtraRow {
  item: InventoryItem
}

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const containerId = computed(() => Number(route.params.id))
const detail = ref<ContainerDetail | null>(null)
const expectedRows = ref<ExpectedRow[]>([])
const extraRows = ref<ExtraRow[]>([])
const deep = ref(false)
const loading = ref(true)
const error = ref('')
const scanValue = ref('')
const submitting = ref(false)
const finishedCheck = ref<unknown | null>(null)

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
    const [d, items] = await Promise.all([
      inventoryContainers.getContainer(containerId.value),
      inventoryContainers.listExpectedItemsInContainer(containerId.value, deep.value),
    ])
    detail.value = d
    expectedRows.value = items.map(i => ({item: i, result: 'PENDING'}))
    extraRows.value = []
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.checkContainer.loadError')
  } finally {
    loading.value = false
  }
}

async function reloadOnDeepChange() {
  await load()
}

async function handleScan() {
  const term = scanValue.value.trim()
  if (!term) return
  scanValue.value = ''
  const row = expectedRows.value.find(r => r.item.internalId === term)
  if (row) {
    row.result = 'CONFIRMED'
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

      <div class="flex items-center justify-between mb-4 gap-3 flex-wrap">
        <PageHeader>{{ t('inventory.checkContainer.walkTitle', {name: detail.container.name}) }}</PageHeader>
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
            <PrimaryButton @click="handleScan">
              <font-awesome-icon :icon="['fas', 'barcode']" class="mr-2" />
              {{ t('inventory.checkContainer.scanAction') }}
            </PrimaryButton>
          </div>
          <div class="flex gap-4 text-sm mt-3">
            <SuccessBadge>{{ t('inventory.checkContainer.statusConfirmed') }}: {{ counts.confirmed }}</SuccessBadge>
            <InfoBadge>{{ t('inventory.checkContainer.statusPending') }}: {{ counts.pending }}</InfoBadge>
            <ErrorBadge>{{ t('inventory.checkContainer.statusMissing') }}: {{ counts.missing }}</ErrorBadge>
            <InfoBadge v-if="counts.extra > 0">{{ t('inventory.checkContainer.statusExtra') }}: {{ counts.extra }}</InfoBadge>
          </div>
        </NeutralContainer>

        <SectionHeader>{{ t('inventory.checkContainer.expected') }}</SectionHeader>
        <NeutralContainer class="mb-4">
          <EmptyState v-if="expectedRows.length === 0" :message="t('inventory.checkContainer.expectedEmpty')" />
          <ul v-else class="divide-y divide-(--bg-accent)">
            <li v-for="row in expectedRows" :key="row.item.id" class="py-2 flex items-center gap-3">
              <span class="flex-1">
                <span class="font-medium">{{ row.item.name }}</span>
                <span v-if="row.item.internalId" class="text-xs text-(--text-muted) ml-2">{{ row.item.internalId }}</span>
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
          <SuccessButton :disabled="submitting" @click="finishCheck">
            <font-awesome-icon :icon="['fas', 'check-double']" class="mr-2" />
            {{ submitting ? t('common.saving') : t('inventory.checkContainer.finish') }}
          </SuccessButton>
        </div>
      </template>
    </template>
    <Alert v-else variant="error">{{ error }}</Alert>
  </ViewContent>
</template>
