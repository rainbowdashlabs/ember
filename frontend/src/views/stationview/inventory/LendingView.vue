/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {onMounted, ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import LendingRequestList from '@/views/stationview/inventory/lendingview/LendingRequestList.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import type {LendingRequestResponse, AvailableInventoryEntry} from '@/api/lending'
import * as lending from '@/api/lending'
import {useSession} from '@/composables/useSession'
import {StationPermission} from '@/api/types'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const routes = useInventoryRoutes()

const {t} = useI18n()
const router = useRouter()
const {loaded, hasPermission} = useSession()

const isLendingManager = computed(() => hasPermission(StationPermission.INVENTORY_LENDING_MANAGER))

const activeTab = ref<'offers' | 'requests'>('offers')

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
    name: routes.lendingCreate,
    query: {
      inventoryId: String(item.inventoryId),
      stationId: String(item.stationId),
      stationName: item.stationName,
    },
  })
}

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
  <ViewContent
      :title="t('pages.inventory-lending.title')"
      :subtitle="t('pages.inventory-lending.subtitle')"
  >
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 mb-4">
      <PrimaryButton v-if="isLendingManager" :icon="['fas', 'calendar-xmark']" @click="router.push({name: routes.lendingBlocks})">
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

    <template v-if="activeTab === 'offers'">
      <SearchInput
          v-model="searchQuery"
          :placeholder="t('lending.searchInventory')"
          class="mb-4"
      />

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

      <AsyncSection
          :empty="filteredItems.length === 0"
          :empty-message="t('lending.noAvailable')"
          :error="availableError"
          :loading="loadingAvailable"
      >
        <div class="flex flex-col gap-2">
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
                  <template v-if="item.distanceKm != null">
                    · {{ t('lendingDistance.distanceKm', {distance: item.distanceKm.toFixed(1)}) }}
                  </template>
                  <template v-else>
                    · {{ t('lendingDistance.distanceUnknown') }}
                  </template>
                </span>
              </div>
              <PrimaryButton :icon="['fas', 'paper-plane']" @click="navigateToCreateRequest(item)">
                {{ t('lending.requestItem') }}
              </PrimaryButton>
            </div>
          </NeutralContainer>
        </div>
      </AsyncSection>
    </template>

    <template v-if="activeTab === 'requests'">
      <AsyncSection :error="requestsError" :loading="loadingRequests">
        <template v-if="isLendingManager">
          <SubHeader class="mt-2 mb-2">{{ t('lending.incoming') }}</SubHeader>
          <EmptyState v-if="incoming.length === 0" compact>{{ t('lending.noIncoming') }}</EmptyState>
          <LendingRequestList :entries="incoming" direction="incoming"/>
        </template>

        <SubHeader class="mt-6 mb-2">{{ t('lending.outgoing') }}</SubHeader>
        <EmptyState v-if="outgoing.length === 0" compact>{{ t('lending.noOutgoing') }}</EmptyState>
        <LendingRequestList :entries="outgoing" direction="outgoing"/>
      </AsyncSection>
    </template>
  </ViewContent>
</template>
