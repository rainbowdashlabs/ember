/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InventoryStatsPanel from '@/views/stationview/inventory/detailview/InventoryStatsPanel.vue'
import InventoryTabs from './clusterinventoryview/InventoryTabs.vue'
import {clusterInventory} from '@/api'
import type {ClusterInventoryStat, ClusterSizeStat} from '@/api/clusterInventory'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * How much of each kind of thing the association owns and where it stands.
 *
 * <p>One block per inventory rather than one pile of everything: the question an association has is how
 * many jackets there are and how many of those are still in its store, which a single total answers for
 * nothing. The sizes sit inside the block they belong to for the same reason.
 *
 * <p>Only what the association owns is counted. Gear a station bought itself belongs to that station and
 * is none of this page's business, whoever happens to be wearing it.
 */
const {t} = useI18n()

const stats = ref<ClusterInventoryStat[]>([])

const {loading, error} = useAsyncLoader(async () => {
  stats.value = await clusterInventory.statistics()
})

/** What is not resting in the association's own store, however far it has gone. */
function outOf(stat: ClusterInventoryStat | ClusterSizeStat): number {
  return stat.atStation + stat.withMember
}

function sizeStats(stat: ClusterInventoryStat) {
  return stat.sizes.map(size => ({
    size: {id: size.sizeId, inventoryId: stat.inventoryId, label: size.label, position: 0, note: ''},
    total: size.total,
    assigned: outOf(size),
    free: size.inStore,
    lost: size.lost,
  }))
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-inventory-statistics.subtitle')"
               :title="t('pages.cluster-inventory-statistics.title')">
    <div class="space-y-6">
      <InventoryTabs/>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="stats.length === 0">{{ t('clusterInventory.empty') }}</EmptyState>

        <template v-else>
          <MutedText tag="p" size="sm">{{ t('clusterInventory.statsScope') }}</MutedText>

          <NeutralContainer v-for="stat in stats" :key="stat.inventoryId"
                            data-testid="cluster-stat-group" class="space-y-3">
            <SectionHeader>{{ stat.inventoryName }}</SectionHeader>
            <InventoryStatsPanel
                :total-count="stat.total"
                :free-count="stat.inStore"
                :assigned-count="outOf(stat)"
                :lost-count="stat.lost"
                :lent-out-count="stat.lent"
                :has-sizes="stat.sizes.length > 0"
                :size-stats="sizeStats(stat)"
            />
          </NeutralContainer>
        </template>
      </template>
    </div>
  </ViewContent>
</template>
