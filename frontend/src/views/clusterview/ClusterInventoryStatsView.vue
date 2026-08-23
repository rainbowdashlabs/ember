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
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import InventoryStatsPanel from '@/views/stationview/inventory/detailview/InventoryStatsPanel.vue'
import InventoryTabs from './clusterinventoryview/InventoryTabs.vue'
import {clusterInventory} from '@/api'
import type {ClusterItem} from '@/api/clusterInventory'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * How much of the association's gear there is and where it stands.
 *
 * <p>Counted over what the association owns, which is the summary the station's own panel already
 * draws. This is not the reporting across a whole association that is still deferred: it says how
 * many, not how well.
 */
const {t} = useI18n()

const items = ref<ClusterItem[]>([])

const {loading, error} = useAsyncLoader(async () => {
  items.value = await clusterInventory.listItems()
})

const counts = computed(() => {
  let free = 0
  let away = 0
  let lost = 0
  for (const item of items.value) {
    if (item.custody === 'WITH_OWNER') free++
    else if (item.custody === 'LOST') lost++
    else away++
  }
  return {total: items.value.length, free, away, lost}
})

/**
 * The same counts once more, cut by size.
 *
 * <p>An association buying two hundred jackets has to know how many of each size are still in its own
 * store, which the four totals cannot say. Gear from an inventory that keeps no sizes is left out
 * rather than gathered under a nameless row: it has no size, it is not of an unknown one.
 */
const sizeStats = computed(() => {
  const bySize = new Map<number, {label: string; total: number; assigned: number; free: number; lost: number}>()
  for (const item of items.value) {
    if (item.sizeId == null) continue
    const stat = bySize.get(item.sizeId)
        ?? {label: item.sizeLabel ?? `#${item.sizeId}`, total: 0, assigned: 0, free: 0, lost: 0}
    stat.total++
    if (item.custody === 'WITH_OWNER') stat.free++
    else if (item.custody === 'LOST') stat.lost++
    else stat.assigned++
    bySize.set(item.sizeId, stat)
  }
  return [...bySize.entries()]
      .map(([id, stat]) => ({
        size: {id, inventoryId: 0, label: stat.label, position: 0, note: ''},
        total: stat.total,
        assigned: stat.assigned,
        free: stat.free,
        lost: stat.lost,
      }))
      .sort((a, b) => a.size.label.localeCompare(b.size.label))
})
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-inventory-statistics.subtitle')"
               :title="t('pages.cluster-inventory-statistics.title')">
    <div class="space-y-6">
      <InventoryTabs/>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="counts.total === 0">{{ t('clusterInventory.empty') }}</EmptyState>
        <InventoryStatsPanel
            v-else
            :total-count="counts.total"
            :free-count="counts.free"
            :assigned-count="counts.away"
            :lost-count="counts.lost"
            :lent-out-count="0"
            :has-sizes="sizeStats.length > 0"
            :size-stats="sizeStats"
        />
      </template>
    </div>
  </ViewContent>
</template>
