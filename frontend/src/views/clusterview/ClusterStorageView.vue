/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {RouterLink} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ProgressBar from '@/components/feedback/ProgressBar.vue'
import StoragePresetPanel from '@/views/adminview/adminstorageview/StoragePresetPanel.vue'
import StorageStationTable from '@/views/adminview/adminstorageview/StorageStationTable.vue'
import QuotaDefaultsPanel from './clusterstorageview/QuotaDefaultsPanel.vue'
import StationRoomModal from './clusterstorageview/StationRoomModal.vue'
import {clusterStorage} from '@/api'
import type {ClusterStationRoom, ClusterStorageOverview, QuotaDimensions} from '@/api/clusterStorage'
import {useStorageQuotas, type StorageQuotasPort, type StorageRoomRow} from '@/composables/useStorageQuotas'
import {formatBytes} from '@/util/storage'

const {t} = useI18n()

/** Every dimension left open, which is what the screen reads while nothing has arrived yet. */
const NOTHING_DECIDED: QuotaDimensions = {
  totalBytes: null, kbBytes: null, boardBytes: null,
  imagesBytes: null, pagesBytes: null, perFileBytes: null, perImageBytes: null,
}

/** What the association's overview says about one station, in the shape the shared table reads. */
function asRow(room: ClusterStationRoom): StorageRoomRow {
  const quota = room.resolved.total.bytes
  return {
    stationId: room.stationUid,
    stationName: room.stationName,
    totalBytes: room.usedBytes,
    quotaBytes: quota,
    quotaUsedPercent: quota > 0 ? Math.min(100, Math.round((room.usedBytes * 100) / quota)) : 0,
    categories: room.usage,
    presetId: room.presetId,
    presetName: room.presetName,
    usesOwnBackend: false,
    origin: room.resolved.total.origin,
    ownStore: room.ownStore,
  }
}

const overview = ref<ClusterStorageOverview | null>(null)

const rooms = computed<ClusterStationRoom[]>(() => overview.value?.stations ?? [])
const poolBytes = computed(() => overview.value?.poolBytes ?? null)
const handedOut = computed(() => overview.value?.handedOut ?? 0)
const defaults = computed<QuotaDimensions>(() => overview.value?.defaults ?? NOTHING_DECIDED)

/**
 * The association's own quotas: the pool the instance granted it, what it gives its stations by default, the
 * tiers it keeps, and what each station was granted against what it is using.
 *
 * <p>No recount here: counting the bytes again is the instance's job, and an association has no business
 * asking for it. Nothing defers to anybody either, because this is the body that decides.
 */
const port: StorageQuotasPort = {
  load: async () => {
    const loaded = await clusterStorage.getOverview()
    overview.value = loaded
    return {stations: loaded.stations.map(asRow), tiers: loaded.presets}
  },
  createTier: (values) => clusterStorage.createTier(values),
  updateTier: (tierId, values) => clusterStorage.updateTier(tierId, values),
  deleteTier: (tierId) => clusterStorage.deleteTier(tierId),
  applyTier: (tierId, stationIds) => clusterStorage.applyTier(tierId, stationIds),
  resetStation: (stationId) => clusterStorage.handBackStationRoom(stationId),
}

const {
  stations, tiers, loading, busy, error, reload, run, saveTier, removeTier, applyTier, resetStation,
} = useStorageQuotas(port, {canRecalculate: false, showsOrigin: true, deferToCluster: false})

onMounted(reload)

const usedPercent = computed(() => {
  const total = poolBytes.value
  if (!total) return 0
  return Math.min(100, Math.round((handedOut.value / total) * 100))
})

const editing = ref<ClusterStationRoom | null>(null)
const showRoomModal = ref(false)

function openRoom(stationUid: string) {
  editing.value = rooms.value.find(room => room.stationUid === stationUid) ?? null
  showRoomModal.value = editing.value !== null
}

async function saveRoom(stationUid: string, room: QuotaDimensions) {
  if (await run(() => clusterStorage.setStationRoom(stationUid, room))) showRoomModal.value = false
}

async function handBack(stationUid: string) {
  if (await resetStation(stationUid)) showRoomModal.value = false
}

function saveDefaults(next: QuotaDimensions) {
  return run(() => clusterStorage.setDefaults(next))
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-storage.subtitle')" :title="t('pages.cluster-storage.title')">
    <div class="space-y-4">
      <div class="flex justify-end">
        <RouterLink :to="{name: 'cluster-storage-backend'}" class="text-sm underline">
          {{ t('clusterStorageBackend.linkFromRoom') }}
        </RouterLink>
      </div>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('clusterStorage.poolTitle') }}</SectionHeader>
          <template v-if="poolBytes">
            <p data-testid="cluster-pool-usage">
              {{ t('clusterStorage.poolUsage', {used: formatBytes(handedOut), total: formatBytes(poolBytes)}) }}
            </p>
            <ProgressBar :value="usedPercent"/>
          </template>
          <p v-else class="text-(--text-muted)">{{ t('clusterStorage.noPool') }}</p>
        </NeutralContainer>

        <QuotaDefaultsPanel :busy="busy" :defaults="defaults" @save="saveDefaults"/>

        <StoragePresetPanel :stations="stations" :tiers="tiers"
                            @apply="applyTier" @remove="removeTier" @save="saveTier"/>

        <EmptyState v-if="stations.length === 0">{{ t('clusterStorage.noStations') }}</EmptyState>
        <StorageStationTable v-else :stations="stations" @edit="openRoom" @reset="resetStation"/>
      </template>
    </div>

    <StationRoomModal v-model="showRoomModal" :busy="busy" :station="editing"
                      @hand-back="handBack" @save="saveRoom"/>
  </ViewContent>
</template>
