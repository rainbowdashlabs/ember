/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import DiscoveryGroups from '@/components/discovery/DiscoveryGroups.vue'
import StationMap, {type MapStation} from '@/components/map/StationMap.vue'
import DiscoveryToolbar from '@/views/public/publicdiscoveryview/DiscoveryToolbar.vue'
import {initialSearchTerm, searchDiscovery} from '@/views/public/publicdiscoveryview/discoverySearch'
import {discovery} from '@/api'
import type {DiscoveryEntry} from '@/api/discovery'
import {useSession} from '@/composables/useSession'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {describeFailure, type Failure} from '@/util/failure'
import {apiUrl} from '@/util/apiUrl'

const {t} = useI18n()
const {canManageFederation} = useSession()
const route = useRoute()

/**
 * Loaded during the server render rather than after mount. This page is public and indexed, and a
 * station list that only appears once the browser has taken over is a list no crawler sees.
 *
 * The refresh after a federation action goes through the same call, so the two paths cannot drift.
 */
const {data: stations, status, refresh} = await useAsyncData(
    'public-discovery',
    () => $fetch<DiscoveryEntry[]>(apiUrl('/public/discovery')),
    {default: (): DiscoveryEntry[] => []},
)

const loading = computed(() => status.value === 'pending')
const failure = ref<Failure | null>(null)
const {message: success, flash} = useFlashMessage(3000)
const inviteCode = ref('')
const tab = ref<'list' | 'map'>('list')
const search = ref(initialSearchTerm(route.query.q))

const shown = computed(() => searchDiscovery(stations.value, search.value))

const mapStations = computed<MapStation[]>(() => shown.value
    .filter((s) => typeof s.latitude === 'number' && typeof s.longitude === 'number')
    .map((s) => ({
      uid: `${s.instanceHost ?? ''}/${s.stationUid}`,
      name: s.name,
      latitude: s.latitude as number,
      longitude: s.longitude as number,
      subtitle: [s.city, s.country, s.instanceHost].filter(Boolean).join(', ') || null,
      href: mapLink(s),
      tint: s.isOwnStation ? 'local' : s.alreadyFederated ? 'near' : null,
    })),
)

function mapLink(station: DiscoveryEntry): string | null {
  if (station.publicPageUrl) return station.publicPageUrl
  return station.publicSlug ? `/public/station/${station.publicSlug}` : null
}

/**
 * Asking to federate, and refreshing the list afterwards, which are two things and not one.
 *
 * <p>They shared a `catch`, so a request that went through and a list that failed to come back
 * afterwards read as a request that failed, and the reader asked a second time. The request's own
 * failure is the one worth showing; a stale list is put right by reloading the page.
 */
async function handleConnect(station: DiscoveryEntry) {
  failure.value = null
  try {
    await discovery.requestFederation(station.stationUid)
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  flash(t('discovery.requestSent'))
  try {
    await refresh()
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
}

async function handleInvite(station: DiscoveryEntry) {
  failure.value = null
  try {
    inviteCode.value = await discovery.generateInvite(station.stationUid)
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}
</script>

<template>
  <ViewContent :title="t('pages.public-discovery.title')" :subtitle="t('pages.public-discovery.subtitle')">
  <div class="max-w-5xl mx-auto px-4 py-8">
    <MutedText tag="p" class="mb-6">{{ t('discovery.subtitle') }}</MutedText>

    <FailureAlert :failure="failure" class="mb-2"/>
    <Alert v-if="success" variant="success" class="mb-2">{{ success }}</Alert>

    <div v-if="inviteCode" class="mb-6 p-4 rounded bg-[var(--bg-accent)] border border-[var(--border)]">
      <MutedText size="sm" class="mb-2">{{ t('discovery.inviteHint') }}</MutedText>
      <code class="block rounded bg-[var(--bg)] p-3 font-mono text-center text-lg select-all break-all">{{ inviteCode }}</code>
    </div>

    <AsyncSection :empty="stations.length === 0" :empty-message="t('discovery.empty')" :loading="loading">
      <DiscoveryToolbar v-model:search="search" v-model:tab="tab"/>
      <EmptyState v-if="shown.length === 0" :message="t('discovery.noSearchResults')"/>
      <NeutralContainer v-else-if="tab === 'map'">
        <EmptyState v-if="mapStations.length === 0" :message="t('stationDiscovery.noCoordinatesForFilter')"/>
        <StationMap v-else :stations="mapStations" height="520px"/>
      </NeutralContainer>
      <DiscoveryGroups v-else :stations="shown" :can-connect="canManageFederation()" :show-invite="true" @connect="handleConnect" @invite="handleInvite"/>
    </AsyncSection>
  </div>
  </ViewContent>
</template>
