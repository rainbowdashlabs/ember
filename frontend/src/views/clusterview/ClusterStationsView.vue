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
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {clusterStations} from '@/api'
import type {ClusterStation} from '@/api/clusterStations'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useModalTarget} from '@/composables/useModalTarget'
import {useSession} from '@/composables/useSession'
import {ClusterPermission} from '@/api/clusters'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

const newName = ref('')
const busy = ref(false)

const {config: stations, loading, error, runWith} = useConfigPanel<ClusterStation[]>({
  initial: [],
  fetch: () => clusterStations.listStations(),
})

const {isOpen: showRelease, target: releaseTarget, open: openRelease} = useModalTarget<ClusterStation>()

async function create() {
  if (!newName.value.trim()) return
  await runWith(async () => {
    await clusterStations.createStation(newName.value.trim())
    newName.value = ''
    return clusterStations.listStations()
  }, {busy})
}

async function confirmRelease() {
  const target = releaseTarget.value
  if (!target) return
  await runWith(async () => {
    await clusterStations.releaseStation(target.uid)
    showRelease.value = false
    return clusterStations.listStations()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-stations.subtitle')" :title="t('pages.cluster-stations.title')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer v-if="hasClusterPermission(ClusterPermission.CLUSTER_STATIONS)" class="space-y-4">
        <SectionHeader>{{ t('clusterStations.createTitle') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('clusterStations.createHint') }}</p>

        <div class="space-y-1">
          <FormLabel>{{ t('clusterStations.nameLabel') }}</FormLabel>
          <TextInput v-model="newName" :placeholder="t('clusterStations.namePlaceholder')"/>
        </div>

        <PrimaryButton :disabled="busy || !newName.trim()" @click="create">
          {{ t('common.create') }}
        </PrimaryButton>
      </NeutralContainer>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="stations.length === 0">{{ t('clusterStations.empty') }}</EmptyState>
        <div v-else class="space-y-2">
          <NeutralContainer
              v-for="station in stations"
              :key="station.uid"
              class="flex items-center justify-between gap-4"
          >
            <span class="font-medium">{{ station.name }}</span>
            <SecondaryButton
                v-if="hasClusterPermission(ClusterPermission.CLUSTER_STATIONS)"
                :disabled="busy"
                @click="openRelease(station)"
            >
              {{ t('clusterStations.release') }}
            </SecondaryButton>
          </NeutralContainer>
        </div>
      </template>
    </div>

    <ConfirmDeleteModal
        v-model="showRelease"
        :message="t('clusterStations.releaseMessage', {name: releaseTarget?.name ?? ''})"
        @confirm="confirmRelease"
    />
  </ViewContent>
</template>
