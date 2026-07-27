/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import DiscoveryGrid from '@/components/discovery/DiscoveryGrid.vue'
import StationMap, {type MapStation} from '@/components/map/StationMap.vue'
import {discovery} from '@/api'
import type {DiscoveryEntry} from '@/api/discovery'
import {useSession} from '@/composables/useSession'
import {useFlashMessage} from '@/composables/useFlashMessage'

const {t} = useI18n()
const {canManageFederation} = useSession()

const stations = ref<DiscoveryEntry[]>([])
const loading = ref(true)
const error = ref('')
const {message: success, flash} = useFlashMessage(3000)
const inviteCode = ref('')
const tab = ref<'list' | 'map'>('list')

const mapStations = computed<MapStation[]>(() => stations.value
    .filter((s) => typeof s.latitude === 'number' && typeof s.longitude === 'number')
    .map((s) => ({
      uid: s.stationUid,
      name: s.name,
      latitude: s.latitude as number,
      longitude: s.longitude as number,
      subtitle: [s.city, s.country].filter(Boolean).join(', ') || null,
      href: s.publicSlug ? `/public/station/${s.publicSlug}` : null,
      tint: s.isOwnStation ? 'local' : s.alreadyFederated ? 'near' : null,
    })),
)

onMounted(async () => {
  try {
    stations.value = await discovery.listDiscoverable()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
})

async function handleConnect(station: DiscoveryEntry) {
  try {
    await discovery.requestFederation(station.stationUid)
    flash(t('discovery.requestSent'))
    stations.value = await discovery.listDiscoverable()
  } catch {
    error.value = t('discovery.requestError')
  }
}

async function handleInvite(station: DiscoveryEntry) {
  try {
    inviteCode.value = await discovery.generateInvite(station.stationUid)
  } catch {
    error.value = t('common.error')
  }
}
</script>

<template>
  <ViewContent :title="t('pages.public-discovery.title')" :subtitle="t('pages.public-discovery.subtitle')">
  <div class="max-w-5xl mx-auto px-4 py-8">
    <MutedText tag="p" class="mb-6">{{ t('discovery.subtitle') }}</MutedText>

    <Alert v-if="error" variant="error" class="mb-2">{{ error }}</Alert>
    <Alert v-if="success" variant="success" class="mb-2">{{ success }}</Alert>

    <!-- Invite code display -->
    <div v-if="inviteCode" class="mb-6 p-4 rounded bg-[var(--bg-accent)] border border-[var(--border)]">
      <MutedText size="sm" class="mb-2">{{ t('discovery.inviteHint') }}</MutedText>
      <code class="block rounded bg-[var(--bg)] p-3 font-mono text-center text-lg select-all break-all">{{ inviteCode }}</code>
    </div>

    <Spinner v-if="loading"/>
    <EmptyState v-else-if="stations.length === 0">{{ t('discovery.empty') }}</EmptyState>
    <template v-else>
      <div class="mb-4 flex justify-end gap-1">
        <SelectionToggleButton :selected="tab === 'list'" @toggle="tab = 'list'">
          <font-awesome-icon :icon="['fas', 'list']" class="mr-1"/>
          {{ t('stationDiscovery.listTab') }}
        </SelectionToggleButton>
        <SelectionToggleButton :selected="tab === 'map'" @toggle="tab = 'map'">
          <font-awesome-icon :icon="['fas', 'map-location-dot']" class="mr-1"/>
          {{ t('stationDiscovery.mapTab') }}
        </SelectionToggleButton>
      </div>
      <NeutralContainer v-if="tab === 'map'">
        <EmptyState v-if="mapStations.length === 0">{{ t('stationDiscovery.noCoordinatesForFilter') }}</EmptyState>
        <StationMap v-else :stations="mapStations" height="520px"/>
      </NeutralContainer>
      <DiscoveryGrid v-else :stations="stations" :can-connect="canManageFederation()" :show-invite="true" @connect="handleConnect" @invite="handleInvite"/>
    </template>
  </div>
  </ViewContent>
</template>
