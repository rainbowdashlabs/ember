/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import DiscoveryGrid from '@/components/discovery/DiscoveryGrid.vue'
import {discovery, federation} from '@/api'
import type {DiscoveryEntry} from '@/api/discovery'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useFlashMessage} from '@/composables/useFlashMessage'

const {t} = useI18n()
const {loaded, canManageFederation} = useSession()

const stations = ref<DiscoveryEntry[]>([])
const {message: success, flash} = useFlashMessage(3000)

const {loading, error, reload: loadAll} = useAsyncLoader(async () => {
  const [stationsList, partners] = await Promise.all([
    discovery.listDiscoverable(),
    federation.listPartners(),
  ])
  const partnerUids = new Set(partners.map(p => p.partner.partnerStationId))
  stations.value = stationsList.map(s => ({
    ...s,
    alreadyFederated: s.alreadyFederated || partnerUids.has(s.stationUid),
  }))
}, {autoLoad: false})

async function handleConnect(station: DiscoveryEntry) {
  try {
    await discovery.requestFederation(station.stationUid)
    flash(t('discovery.requestSent'))
    await loadAll()
  } catch {
    error.value = t('discovery.requestError')
  }
}

watch(loaded, (v) => { if (v) loadAll() }, {immediate: true})
</script>

<template>
  <ViewContent
      :title="t('pages.station-discovery.title')"
      :subtitle="t('pages.station-discovery.subtitle')"
  >
    <MutedText tag="p" class="mb-6">{{ t('discovery.subtitle') }}</MutedText>

    <Alert v-if="error" variant="error" class="mb-2">{{ error }}</Alert>
    <Alert v-if="success" variant="success" class="mb-2">{{ success }}</Alert>

    <Spinner v-if="loading"/>
    <EmptyState v-else-if="stations.length === 0">{{ t('discovery.empty') }}</EmptyState>
    <DiscoveryGrid v-else :stations="stations" :can-connect="canManageFederation()" :show-invite="false" @connect="handleConnect"/>
  </ViewContent>
</template>
