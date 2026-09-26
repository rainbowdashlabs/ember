/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RecordCardControls from '@/components/table/RecordCardControls.vue'
import TableFilterDialog from '@/components/table/TableFilterDialog.vue'
import InventoryTabs from './clusterinventoryview/InventoryTabs.vue'
import OutStationFilter from './clusteroutatstationsview/OutStationFilter.vue'
import OutStationGroup from './clusteroutatstationsview/OutStationGroup.vue'
import {useOutItemTable} from './clusteroutatstationsview/useOutItemTable'
import {clusterInventory} from '@/api'
import type {ClusterItem} from '@/api/clusterInventory'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useBreakpoint} from '@/composables/useBreakpoint'

/**
 * Where the association's gear is when it is not in the association's own store.
 *
 * <p>A custody query rather than an inventory one: the same rows the store shows, filtered to the
 * ones that have gone somewhere, and grouped by the station holding them. An item on its way reads as
 * in transit at both ends, which is the point of splitting the journey in two.
 *
 * <p>The blocks are parts of one table, so a sort or a filter chosen in one holds for every station.
 */
const {t} = useI18n()
const {isMobile} = useBreakpoint()

const items = ref<ClusterItem[]>([])

const {loading, failure} = useAsyncLoader(async () => {
  items.value = await clusterInventory.listItems()
})

/** Everything that is not resting at the association. */
const away = computed(() => items.value.filter(item => item.custody !== 'WITH_OWNER'))

/** The station the list is narrowed to, empty for all of them. */
const station = ref('')

function stationOf(item: ClusterItem): string {
  return item.stationName ?? t('clusterInventory.inTransit')
}

/**
 * Only the stations actually holding something. An association with fifty stations and gear at six of
 * them offers six, because a picker listing the other forty-four is the same scrolling problem again.
 */
const stations = computed(() => [...new Set(away.value.map(stationOf))].sort((a, b) => a.localeCompare(b)))

const atChosenStation = computed(() =>
    station.value ? away.value.filter(item => stationOf(item) === station.value) : away.value)

const table = useOutItemTable(atChosenStation)

const byStation = computed(() => {
  const groups = new Map<string, ClusterItem[]>()
  for (const item of table.rows) {
    const key = stationOf(item)
    const bucket = groups.get(key)
    if (bucket) bucket.push(item)
    else groups.set(key, [item])
  }
  return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b))
})
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-inventory-out.subtitle')" :title="t('pages.cluster-inventory-out.title')">
    <div class="space-y-6">
      <InventoryTabs/>

      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

      <EmptyState v-if="!loading && away.length === 0">{{ t('clusterInventory.outEmpty') }}</EmptyState>
      <template v-else-if="!loading">
        <OutStationFilter v-model="station" :stations="stations" :table="table"/>
        <RecordCardControls v-if="isMobile" :table="table"/>
        <OutStationGroup v-for="[name, stationItems] in byStation" :key="name" :items="stationItems" :station="name" :table="table"/>
        <MutedText v-if="byStation.length === 0" size="sm" tag="p">{{ t('clusterInventory.outNoMatch') }}</MutedText>
        <TableFilterDialog :table="table"/>
      </template>
    </div>
  </ViewContent>
</template>
