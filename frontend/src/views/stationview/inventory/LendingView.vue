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
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import LendingRequestList from '@/views/stationview/inventory/lendingview/LendingRequestList.vue'
import LendingOfferList from '@/views/stationview/inventory/lendingview/LendingOfferList.vue'
import LendingOfferFilters from '@/views/stationview/inventory/lendingview/LendingOfferFilters.vue'
import LendingTabs from '@/views/stationview/inventory/lendingview/LendingTabs.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {LendingEmptyReasonName} from '@/api/lending'
import {StationPermission, type AvailableInventoryEntry, type LendingRequestResponse} from '@/api/generated/schema'
import * as lending from '@/api/lending'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const routes = useInventoryRoutes()

const {t} = useI18n()
const router = useRouter()
const {loaded, hasPermission} = useSession()

const isLendingManager = computed(() => hasPermission(StationPermission.INVENTORY_LENDING_MANAGER))

const activeTab = ref<'offers' | 'requests'>('offers')

const availableItems = ref<AvailableInventoryEntry[]>([])
const emptyReason = ref<LendingEmptyReasonName | null>(null)
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

/**
 * Why the offers list is empty. The server distinguishes exactly two situations and says nothing
 * finer, so an empty screen reads as an answer rather than as a fault. A search that filters
 * everything away locally is the reader's own doing and keeps the plain message.
 */
const emptyMessage = computed(() => {
  if (availableItems.value.length > 0) return t('lending.noAvailable')
  if (emptyReason.value === 'NOTHING_SHARED') return t('lending.nothingShared')
  if (emptyReason.value === 'NOTHING_FREE') return t('lending.nothingFree')
  return t('lending.noAvailable')
})

/**
 * What the partners have free for the period the filter names.
 *
 * <p>Asked for only once the session is there, and the offers wait for it from the first render on.
 */
const {
    loading: loadingAvailable,
    failure: availableFailure,
    reload: loadAvailable,
} = useAsyncLoader(async (isCurrent) => {
    const options: { q?: string; from?: string; to?: string } = {}
    if (filterDateFrom.value) options.from = filterDateFrom.value
    if (filterDateTo.value) options.to = filterDateTo.value
    const result = await lending.listAvailable(options)
    if (!isCurrent()) return
    availableItems.value = result.entries
    emptyReason.value = result.emptyReason
}, {autoLoad: false})
loadingAvailable.value = true

watch([filterDateFrom, filterDateTo], () => {
  loadAvailable()
})

/**
 * Opens the request form on what the search already asked for.
 *
 * <p>The period is what the search counted against: the number beside an inventory is what is free
 * in those days, not what exists. Sending somebody to a form that starts on no dates at all makes
 * them type the same two dates again, and any pair other than the one they searched for makes the
 * count they clicked on wrong.
 */
function navigateToCreateRequest(item: AvailableInventoryEntry) {
  const query: Record<string, string> = {
    inventoryId: String(item.inventoryId),
    stationId: String(item.stationId),
    stationName: item.stationName,
  }
  if (filterDateFrom.value) query.dateFrom = filterDateFrom.value
  if (filterDateTo.value) query.dateTo = filterDateTo.value
  router.push({name: routes.lendingCreate, query})
}

const requests = ref<LendingRequestResponse[]>([])
const requestSuccess = ref('')

const {
    loading: loadingRequests,
    failure: requestsFailure,
    reload: loadRequests,
} = useAsyncLoader(async () => {
    requests.value = await lending.listRequests()
}, {autoLoad: false})

const incoming = computed(() => requests.value.filter(r => r.isOwner))
const outgoing = computed(() => requests.value.filter(r => !r.isOwner))

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
      <ButtonRow>
        <PrimaryButton v-if="isLendingManager" :icon="['fas', 'calendar-xmark']" @click="router.push({name: routes.lendingBlocks})">
          {{ t('lending.blocks') }}
        </PrimaryButton>
        <SecondaryButton
            v-if="isLendingManager && routes.lendingShares"
            :icon="['fas', 'share-nodes']"
            data-testid="lending-shares-link"
            @click="router.push({name: routes.lendingShares})"
        >
          {{ t('lendingShare.overview') }}
        </SecondaryButton>
      </ButtonRow>
    </div>

    <LendingTabs v-model="activeTab" class="mb-4"/>

    <Alert v-if="requestSuccess" variant="success" class="mb-4">{{ requestSuccess }}</Alert>

    <template v-if="activeTab === 'offers'">
      <LendingOfferFilters
          v-model:search-query="searchQuery"
          v-model:date-from="filterDateFrom"
          v-model:date-to="filterDateTo"
          class="mb-4"
      />

      <AsyncSection
          :empty="filteredItems.length === 0"
          :empty-message="emptyMessage"
          :failure="availableFailure"
          :loading="loadingAvailable"
      >
        <LendingOfferList :items="filteredItems" @request="navigateToCreateRequest"/>
      </AsyncSection>
    </template>

    <template v-if="activeTab === 'requests'">
      <AsyncSection :failure="requestsFailure" :loading="loadingRequests">
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
