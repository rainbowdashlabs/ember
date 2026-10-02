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
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import DiscoveryGroups from '@/components/discovery/DiscoveryGroups.vue'
import {discovery} from '@/api'
import type {DiscoveryEntry} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {useDiscoveryViewer} from '@/composables/useDiscoveryViewer'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {describeFailure} from '@/util/failure'

const {t} = useI18n()
const {loaded} = useSession()
const viewer = useDiscoveryViewer(false)

const stations = ref<DiscoveryEntry[]>([])
const {message: success, flash} = useFlashMessage(3000)

const {loading, failure, reload: loadAll} = useAsyncLoader(async () => {
  stations.value = await discovery.listDiscoverable()
}, {autoLoad: false})

/**
 * Asks a station to federate, then reads the list back.
 *
 * <p>The read is answered for separately: a request that went out and a list that then failed to
 * refresh used to report a request that did not, and the reader asks the same station twice.
 */
async function handleConnect(station: DiscoveryEntry) {
  try {
    await discovery.requestFederation(station.stationUid)
    flash(t('discovery.requestSent'))
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('discovery.requestError')}
    return
  }
  await loadAll()
  if (failure.value) failure.value = {...failure.value, message: t('failure.staleAfterAction')}
}

watch(loaded, (v) => { if (v) loadAll() }, {immediate: true})
</script>

<template>
  <ViewContent
      :title="t('pages.station-discovery.title')"
      :subtitle="t('pages.station-discovery.subtitle')"
  >
    <MutedText tag="p" class="mb-6">{{ t('discovery.subtitle') }}</MutedText>

    <FailureAlert :failure="failure" class="mb-2"/>
    <Alert v-if="success" variant="success" class="mb-2">{{ success }}</Alert>

    <AsyncSection :empty="stations.length === 0" :empty-message="t('discovery.empty')" :loading="loading">
      <DiscoveryGroups :stations="stations" :viewer="viewer" @connect="handleConnect"/>
    </AsyncSection>
  </ViewContent>
</template>
