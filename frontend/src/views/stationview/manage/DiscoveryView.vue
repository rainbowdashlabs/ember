/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import DiscoveryGrid from '@/components/discovery/DiscoveryGrid.vue'
import {discovery, federation} from '@/api'
import type {DiscoveryEntry} from '@/api/discovery'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {loaded, canManageFederation} = useSession()

const stations = ref<DiscoveryEntry[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [stationsList, partners] = await Promise.all([
      discovery.listDiscoverable(),
      federation.listPartners(),
    ])
    const partnerUids = new Set(partners.map(p => p.partner.partnerStationId))
    stations.value = stationsList.map(s => ({
      ...s,
      alreadyFederated: s.alreadyFederated || partnerUids.has(s.stationUid),
    }))
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function handleConnect(station: DiscoveryEntry) {
  try {
    await discovery.requestFederation(station.stationUid)
    success.value = t('discovery.requestSent')
    setTimeout(() => { success.value = '' }, 3000)
    await loadAll()
  } catch {
    error.value = t('discovery.requestError')
  }
}

onMounted(() => { if (loaded.value) loadAll() })
watch(loaded, (v) => { if (v) loadAll() })
</script>

<template>
  <ViewContent>
    <SectionHeader class="mb-4">{{ t('discovery.title') }}</SectionHeader>
    <MutedText tag="p" class="mb-6">{{ t('discovery.subtitle') }}</MutedText>

    <Alert v-if="error" variant="error" class="mb-2">{{ error }}</Alert>
    <Alert v-if="success" variant="success" class="mb-2">{{ success }}</Alert>

    <Spinner v-if="loading"/>
    <EmptyState v-else-if="stations.length === 0">{{ t('discovery.empty') }}</EmptyState>
    <DiscoveryGrid v-else :stations="stations" :can-connect="canManageFederation()" :show-invite="false" @connect="handleConnect"/>
  </ViewContent>
</template>
