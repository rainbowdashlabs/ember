/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ProgressBar from '@/components/feedback/ProgressBar.vue'
import StationQuotaRow from './clusterstorageview/StationQuotaRow.vue'
import {clusterGovernance} from '@/api'
import type {ClusterStoragePool} from '@/api/clusterGovernance'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {formatBytes} from '@/util/storage'

const {t} = useI18n()

const busy = ref(false)

const {config: pool, loading, error, runWith} = useConfigPanel<ClusterStoragePool>({
  initial: {handedOut: 0, stations: []},
  fetch: () => clusterGovernance.getStoragePool(),
})

const usedPercent = computed(() => {
  const total = pool.value.poolBytes
  if (!total) return 0
  return Math.min(100, Math.round((pool.value.handedOut / total) * 100))
})

async function saveQuota(stationUid: string, quotaBytes: number | null) {
  await runWith(async () => {
    await clusterGovernance.setStationQuota(stationUid, quotaBytes)
    return clusterGovernance.getStoragePool()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-storage.subtitle')" :title="t('pages.cluster-storage.title')">
    <div class="space-y-4">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('clusterStorage.poolTitle') }}</SectionHeader>
          <template v-if="pool.poolBytes">
            <p>{{ t('clusterStorage.poolUsage', {used: formatBytes(pool.handedOut), total: formatBytes(pool.poolBytes)}) }}</p>
            <ProgressBar :value="usedPercent"/>
          </template>
          <p v-else class="text-(--text-muted)">{{ t('clusterStorage.noPool') }}</p>
        </NeutralContainer>

        <EmptyState v-if="pool.stations.length === 0">{{ t('clusterStorage.noStations') }}</EmptyState>
        <div v-else class="space-y-2">
          <StationQuotaRow
              v-for="station in pool.stations"
              :key="station.stationUid"
              :busy="busy"
              :station="station"
              @save="saveQuota"
          />
        </div>
      </template>
    </div>
  </ViewContent>
</template>
