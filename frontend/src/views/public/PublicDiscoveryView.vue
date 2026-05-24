/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import DiscoveryGrid from '@/components/discovery/DiscoveryGrid.vue'
import {discovery} from '@/api'
import type {DiscoveryEntry} from '@/api/discovery'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {canManageFederation} = useSession()

const stations = ref<DiscoveryEntry[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')
const inviteCode = ref('')

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
    success.value = t('discovery.requestSent')
    setTimeout(() => { success.value = '' }, 3000)
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
  <div class="max-w-5xl mx-auto px-4 py-8">
    <SectionHeader class="mb-4">{{ t('discovery.title') }}</SectionHeader>
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
    <DiscoveryGrid v-else :stations="stations" :can-connect="canManageFederation()" :show-invite="true" @connect="handleConnect" @invite="handleInvite"/>
  </div>
</template>
