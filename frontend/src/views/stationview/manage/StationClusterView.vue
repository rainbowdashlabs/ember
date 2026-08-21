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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ApplicationHistory from './stationclusterview/ApplicationHistory.vue'
import {clusterStations} from '@/api'
import {
  ClusterApplicationStatus,
  type AvailableCluster,
  type StationCluster,
} from '@/api/clusterStations'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

const busy = ref(false)
const chosenCluster = ref('')
const available = ref<AvailableCluster[]>([])

const {config: state, loading, error, runWith} = useConfigPanel<StationCluster>({
  initial: {applications: []},
  fetch: async () => {
    const current = await clusterStations.getStationCluster()
    // Only worth asking which clusters exist while the station could still join one
    available.value = current.clusterUid ? [] : await clusterStations.listAvailableClusters()
    return current
  },
})

const pending = computed(() =>
    state.value.applications.find(a => a.status === ClusterApplicationStatus.PENDING) ?? null)

const history = computed(() =>
    state.value.applications.filter(a => a.status !== ClusterApplicationStatus.PENDING))

async function apply() {
  if (!chosenCluster.value) return
  await runWith(async () => {
    await clusterStations.applyToCluster(chosenCluster.value)
    chosenCluster.value = ''
    return clusterStations.getStationCluster()
  }, {busy})
}

async function withdraw() {
  const open = pending.value
  if (!open) return
  await runWith(async () => {
    await clusterStations.withdrawApplication(open.id)
    available.value = await clusterStations.listAvailableClusters()
    return clusterStations.getStationCluster()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.station-manage-cluster.subtitle')" :title="t('pages.station-manage-cluster.title')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <NeutralContainer v-if="state.clusterUid" class="space-y-2">
          <SectionHeader>{{ state.clusterName }}</SectionHeader>
          <p v-if="state.clusterDescription" class="text-(--text-muted)">{{ state.clusterDescription }}</p>
          <p class="text-sm text-(--text-muted)">{{ t('stationCluster.memberHint') }}</p>
        </NeutralContainer>

        <NeutralContainer v-else-if="pending" class="space-y-3">
          <SectionHeader>{{ t('stationCluster.pendingTitle') }}</SectionHeader>
          <p>{{ t('stationCluster.pendingText', {name: pending.clusterName ?? ''}) }}</p>
          <SecondaryButton :disabled="busy" @click="withdraw">{{ t('stationCluster.withdraw') }}</SecondaryButton>
        </NeutralContainer>

        <NeutralContainer v-else class="space-y-4">
          <SectionHeader>{{ t('stationCluster.applyTitle') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ t('stationCluster.applyHint') }}</p>

          <div class="space-y-1">
            <FormLabel>{{ t('stationCluster.clusterLabel') }}</FormLabel>
            <SelectInput v-model="chosenCluster">
              <option value="">{{ t('stationCluster.clusterPlaceholder') }}</option>
              <option v-for="cluster in available" :key="cluster.uid" :value="cluster.uid">{{ cluster.name }}</option>
            </SelectInput>
          </div>

          <PrimaryButton :disabled="busy || !chosenCluster" @click="apply">
            {{ t('stationCluster.apply') }}
          </PrimaryButton>
        </NeutralContainer>

        <ApplicationHistory :applications="history"/>
      </template>
    </div>
  </ViewContent>
</template>
