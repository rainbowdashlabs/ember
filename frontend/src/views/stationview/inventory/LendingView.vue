/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import type {LendingRequestResponse, LendingStatusName, AvailableInventoryEntry} from '@/api/lending'
import {LendingStatus} from '@/api/lending'
import * as lending from '@/api/lending'
import {useSession} from '@/composables/useSession'
import {StationPermission} from '@/api/types'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()
const router = useRouter()
const {loaded, hasPermission} = useSession()

const isLendingManager = computed(() => hasPermission(StationPermission.INVENTORY_LENDING_MANAGER))

// Tab state
const activeTab = ref<'offers' | 'requests'>('offers')

// -- Offers tab --
const availableItems = ref<AvailableInventoryEntry[]>([])
const loadingAvailable = ref(true)
const availableError = ref('')
const searchQuery = ref('')
const filterDateFrom = ref('')
const filterDateTo = ref('')

const filteredItems = computed(() => {
  if (!searchQuery.value.trim()) return availableItems.value
  const q = searchQuery.value.toLowerCase()
  return availableItems.value.filter(
      item => item.inventoryName.toLowerCase().includes(q) || item.stationName.toLowerCase().includes(q),
  )
})

async function loadAvailable() {
  loadingAvailable.value = true
  availableError.value = ''
  try {
    const options: { q?: string; from?: string; to?: string } = {}
    if (filterDateFrom.value) options.from = filterDateFrom.value
    if (filterDateTo.value) options.to = filterDateTo.value
    availableItems.value = await lending.listAvailable(options)
  } catch {
    availableError.value = t('lending.loadError')
  } finally {
    loadingAvailable.value = false
  }
}

watch([filterDateFrom, filterDateTo], () => {
  loadAvailable()
})

function navigateToCreateRequest(item: AvailableInventoryEntry) {
  router.push({
    name: 'inventory-lending-create',
    query: {
      inventoryId: String(item.inventoryId),
      stationId: String(item.stationId),
      stationName: item.stationName,
    },
  })
}

// -- Requests tab --
const requests = ref<LendingRequestResponse[]>([])
const loadingRequests = ref(true)
const requestsError = ref('')
const requestSuccess = ref('')

const incoming = computed(() => requests.value.filter(r => r.isOwner))
const outgoing = computed(() => requests.value.filter(r => !r.isOwner))

async function loadRequests() {
  loadingRequests.value = true
  requestsError.value = ''
  try {
    requests.value = await lending.listRequests()
  } catch {
    requestsError.value = t('lending.loadError')
  } finally {
    loadingRequests.value = false
  }
}

function statusBadge(status: LendingStatusName) {
  switch (status) {
    case LendingStatus.APPROVED:
    case LendingStatus.LENT:
      return 'success'
    case LendingStatus.DECLINED:
      return 'error'
    case LendingStatus.REQUESTED:
    case LendingStatus.RETURNED:
      return 'secondary'
    case LendingStatus.CLOSED:
      return 'info'
    default:
      return 'secondary'
  }
}

function formatDate(d: string | null): string {
  if (!d) return '-'
  return new Date(d).toLocaleDateString('de-DE')
}

// Load data
onMounted(() => {
  if (loaded.value) {
    loadAvailable()
    loadRequests()
  }
})

watch(loaded, (v) => {
  if (v) {
    loadAvailable()
    loadRequests()
  }
})
</script>

<template>
  <ViewContent>
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 mb-4">
      <SectionHeader>{{ t('lending.title') }}</SectionHeader>
      <PrimaryButton v-if="isLendingManager" :icon="['fas', 'calendar-xmark']" @click="router.push({name: 'inventory-lending-blocks'})">
        {{ t('lending.blocks') }}
      </PrimaryButton>
    </div>

    <!-- Tab toggle -->
    <div class="flex gap-2 mb-4">
      <SelectionToggleButton :selected="activeTab === 'offers'" @toggle="activeTab = 'offers'">
        <font-awesome-icon :icon="['fas', 'boxes-stacked']" class="mr-1"/>
        {{ t('lending.tabs.offers') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="activeTab === 'requests'" @toggle="activeTab = 'requests'">
        <font-awesome-icon :icon="['fas', 'list']" class="mr-1"/>
        {{ t('lending.tabs.requests') }}
      </SelectionToggleButton>
    </div>

    <Alert v-if="requestSuccess" variant="success" class="mb-4">{{ requestSuccess }}</Alert>

    <!-- Offers tab -->
    <template v-if="activeTab === 'offers'">
      <TextInput
          v-model="searchQuery"
          :placeholder="t('lending.searchInventory')"
          class="mb-4"
      />

      <!-- Date range filter -->
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
        <div>
          <FieldLabel class="mb-1">{{ t('lending.dateFrom') }}</FieldLabel>
          <DateInput v-model="filterDateFrom"/>
        </div>
        <div>
          <FieldLabel class="mb-1">{{ t('lending.dateTo') }}</FieldLabel>
          <DateInput v-model="filterDateTo"/>
        </div>
      </div>

      <Spinner v-if="loadingAvailable"/>
      <Alert v-else-if="availableError" variant="error">{{ availableError }}</Alert>
      <p v-else-if="filteredItems.length === 0" class="text-sm text-[var(--text-muted)]">
        {{ t('lending.noAvailable') }}
      </p>
      <div v-else class="flex flex-col gap-2">
        <SubHeader class="mb-1">{{ t('lending.availableItems') }}</SubHeader>
        <NeutralContainer v-for="item in filteredItems" :key="`${item.stationId}-${item.inventoryId}`">
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div class="flex flex-col gap-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">{{ item.inventoryName }}</span>
                <StationBadge :station-name="item.stationName"/>
              </div>
              <span class="text-xs text-[var(--text-muted)]">
                {{ item.availableCount }} {{ t('lending.available') }}
              </span>
            </div>
            <PrimaryButton :icon="['fas', 'paper-plane']" @click="navigateToCreateRequest(item)">
              {{ t('lending.requestItem') }}
            </PrimaryButton>
          </div>
        </NeutralContainer>
      </div>
    </template>

    <!-- Requests tab -->
    <template v-if="activeTab === 'requests'">
      <Spinner v-if="loadingRequests"/>
      <Alert v-else-if="requestsError" variant="error">{{ requestsError }}</Alert>

      <template v-else>
        <!-- Incoming requests (manager only) -->
        <template v-if="isLendingManager">
        <SubHeader class="mt-2 mb-2">{{ t('lending.incoming') }}</SubHeader>
        <p v-if="incoming.length === 0" class="text-sm text-[var(--text-muted)]">{{ t('lending.noIncoming') }}</p>
        <div class="flex flex-col gap-2">
          <NeutralContainer v-for="req in incoming" :key="req.request.id"
                            class="cursor-pointer hover:border-primary transition-colors"
                            @click="router.push({name: 'inventory-lending-request', params: {id: req.request.id}})">
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div class="flex flex-col gap-0.5">
                <div>
                  <span class="font-medium">{{ req.requestingStationName }}</span>
                  <span class="text-sm text-[var(--text-muted)] ml-2">
                    {{ formatDate(req.request.requestedDateFrom) }}
                    <template v-if="req.request.requestedDateTo"> - {{ formatDate(req.request.requestedDateTo) }}</template>
                  </span>
                </div>
                <span v-if="req.itemSummary" class="text-xs text-[var(--text-muted)]">{{ req.itemSummary }}</span>
              </div>
              <div class="flex items-center gap-2">
                <SuccessBadge v-if="statusBadge(req.request.status) === 'success'">{{ t(`lending.status.${req.request.status}`) }}</SuccessBadge>
                <ErrorBadge v-else-if="statusBadge(req.request.status) === 'error'">{{ t(`lending.status.${req.request.status}`) }}</ErrorBadge>
                <InfoBadge v-else-if="statusBadge(req.request.status) === 'info'">{{ t(`lending.status.${req.request.status}`) }}</InfoBadge>
                <SecondaryBadge v-else>{{ t(`lending.status.${req.request.status}`) }}</SecondaryBadge>
                <ErrorBadge v-if="req.overdue">{{ t('lending.overdue') }}</ErrorBadge>
              </div>
            </div>
          </NeutralContainer>
        </div>
        </template>

        <!-- Outgoing requests -->
        <SubHeader class="mt-6 mb-2">{{ t('lending.outgoing') }}</SubHeader>
        <p v-if="outgoing.length === 0" class="text-sm text-[var(--text-muted)]">{{ t('lending.noOutgoing') }}</p>
        <div class="flex flex-col gap-2">
          <NeutralContainer v-for="req in outgoing" :key="req.request.id"
                            class="cursor-pointer hover:border-primary transition-colors"
                            @click="router.push({name: 'inventory-lending-request', params: {id: req.request.id}})">
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div class="flex flex-col gap-0.5">
                <div>
                  <span class="font-medium">{{ req.owningStationName }}</span>
                  <span class="text-sm text-[var(--text-muted)] ml-2">
                    {{ formatDate(req.request.requestedDateFrom) }}
                    <template v-if="req.request.requestedDateTo"> - {{ formatDate(req.request.requestedDateTo) }}</template>
                  </span>
                </div>
                <span v-if="req.itemSummary" class="text-xs text-[var(--text-muted)]">{{ req.itemSummary }}</span>
              </div>
              <div class="flex items-center gap-2">
                <SuccessBadge v-if="statusBadge(req.request.status) === 'success'">{{ t(`lending.status.${req.request.status}`) }}</SuccessBadge>
                <ErrorBadge v-else-if="statusBadge(req.request.status) === 'error'">{{ t(`lending.status.${req.request.status}`) }}</ErrorBadge>
                <InfoBadge v-else-if="statusBadge(req.request.status) === 'info'">{{ t(`lending.status.${req.request.status}`) }}</InfoBadge>
                <SecondaryBadge v-else>{{ t(`lending.status.${req.request.status}`) }}</SecondaryBadge>
                <ErrorBadge v-if="req.overdue">{{ t('lending.overdue') }}</ErrorBadge>
              </div>
            </div>
          </NeutralContainer>
        </div>
      </template>
    </template>
  </ViewContent>
</template>
