/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import DiscoveryExplorer from '@/components/discovery/DiscoveryExplorer.vue'
import {initialSearchTerm} from '@/components/discovery/discoverySearch'
import {discovery} from '@/api'
import type {DiscoveryEntry} from '@/api/generated/schema'
import {useDiscoveryViewer} from '@/composables/useDiscoveryViewer'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {useSession} from '@/composables/useSession'
import {describeFailure, type Failure} from '@/util/failure'
import {apiUrl} from '@/util/apiUrl'

const {t} = useI18n()
const viewer = useDiscoveryViewer(true)
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
const {sessionInfo, loaded} = useSession()

/**
 * The list for the reader. The server render knows nobody, so it lists the stations as a stranger sees
 * them; somebody signed in is listed again from the browser, which names the station they act for, so
 * each tile says whether they may ask it and whether it is their own.
 */
async function reload() {
  if (sessionInfo.value) {
    stations.value = await discovery.listDiscoverable()
  } else {
    await refresh()
  }
}

watch(loaded, (ready) => {
  if (!ready || !sessionInfo.value) return
  reload().catch((e) => { failure.value = describeFailure(e, t) })
}, {immediate: true})
const {message: success, flash} = useFlashMessage(3000)
const inviteCode = ref('')
const search = ref(initialSearchTerm(route.query.q))

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
    await reload()
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
      <DiscoveryExplorer
          v-model:search="search"
          :stations="stations"
          :viewer="viewer"
          selection-key="station"
          @connect="handleConnect"
          @invite="handleInvite"
      />
    </AsyncSection>
  </div>
  </ViewContent>
</template>
