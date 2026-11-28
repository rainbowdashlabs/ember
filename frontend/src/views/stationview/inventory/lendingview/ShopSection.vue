/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import MutedText from '@/components/typography/MutedText.vue'
import * as lending from '@/api/lending'
import type {ProductSummary} from '@/api/generated/schema'
import type {LendingEmptyReasonName} from '@/api/lending'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import ShopFilters from './ShopFilters.vue'
import ProductTile from './ProductTile.vue'
import {useShopQuery} from './useShopQuery'

/**
 * The lending shop: every partner's products as tiles, narrowed by days, a search, a station and an
 * order. Days and search are asked of the partners, a typed search only once typing pauses; the
 * station and the order narrow what came back.
 */
const {t} = useI18n()
const {dateFrom, dateTo, search, station, sort} = useShopQuery()

/** How long typing has to pause before the partners are asked again. */
const SEARCH_PAUSE_MS = 300

const products = ref<ProductSummary[]>([])
const emptyReason = ref<LendingEmptyReasonName | null>(null)
/** Every partner station seen in an answer, so a chosen one keeps its name when it offers nothing. */
const knownStations = ref(new Map<string, string>())

const {loading, failure, reload} = useAsyncLoader(async (isCurrent) => {
  const answer = await lending.listProducts({
    q: search.value.trim() || undefined,
    from: dateFrom.value || undefined,
    to: dateTo.value || undefined,
  })
  if (!isCurrent()) return
  products.value = answer.products
  emptyReason.value = answer.emptyReason
  for (const summary of answer.products) knownStations.value.set(summary.stationId, summary.stationName)
})

let searchTimer: ReturnType<typeof setTimeout> | null = null

function cancelPendingSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = null
}

watch([dateFrom, dateTo, search], ([from, to], [oldFrom, oldTo]) => {
  cancelPendingSearch()
  if (from !== oldFrom || to !== oldTo) {
    reload()
    return
  }
  searchTimer = setTimeout(() => {
    searchTimer = null
    reload()
  }, SEARCH_PAUSE_MS)
})
onBeforeUnmount(cancelPendingSearch)

const stations = computed(() => {
  const options = [...knownStations.value].map(([id, name]) => ({id, name}))
  if (station.value && !knownStations.value.has(station.value)) {
    options.push({id: station.value, name: t('lendingShop.selectedStation')})
  }
  return options.sort((a, b) => a.name.localeCompare(b.name))
})

const shown = computed(() => {
  const narrowed = station.value
      ? products.value.filter(summary => summary.stationId === station.value)
      : products.value
  if (sort.value !== 'name') return narrowed
  return [...narrowed].sort((a, b) => a.product.name.localeCompare(b.product.name))
})

const emptyMessage = computed(() => {
  if (emptyReason.value === 'NOTHING_SHARED') return t('lending.nothingShared')
  if (station.value && products.value.length > 0) return t('lendingShop.stationOffersNothing')
  if (search.value.trim()) return t('lendingShop.noMatch')
  if (emptyReason.value === 'NOTHING_FREE') return t('lending.nothingFree')
  return t('lending.noAvailable')
})

const countLine = computed(() => shown.value.length === 1
    ? t('lendingShop.countOne')
    : t('lendingShop.count', {count: shown.value.length}))
</script>

<template>
  <div class="space-y-4">
    <ShopFilters
        v-model:search="search"
        v-model:date-from="dateFrom"
        v-model:date-to="dateTo"
        v-model:station="station"
        v-model:sort="sort"
        :stations="stations"
    />
    <MutedText v-if="!dateFrom" tag="p" data-testid="lending-shop-today-hint">{{ t('lendingShop.todayHint') }}</MutedText>
    <AsyncSection :empty="shown.length === 0" :empty-message="emptyMessage" :failure="failure" :loading="loading">
      <MutedText tag="p" class="mb-2">{{ countLine }}</MutedText>
      <ul class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        <li v-for="summary in shown" :key="`${summary.stationId}-${summary.product.inventoryId}`">
          <ProductTile :summary="summary"/>
        </li>
      </ul>
    </AsyncSection>
  </div>
</template>
