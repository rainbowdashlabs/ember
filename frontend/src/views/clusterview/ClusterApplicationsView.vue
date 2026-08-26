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
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SingleFieldModal from '@/components/feedback/SingleFieldModal.vue'
import {clusterStations} from '@/api'
import {ClusterApplicationStatus, type ClusterApplication} from '@/api/clusterStations'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useModalTarget} from '@/composables/useModalTarget'
import {formatDate} from '@/util/format'

const {t} = useI18n()

const busy = ref(false)
const denyReason = ref('')

const {config: applications, loading, error, runWith} = useConfigPanel<ClusterApplication[]>({
  initial: [],
  fetch: () => clusterStations.listApplications(),
})

const pending = computed(() => applications.value.filter(a => a.status === ClusterApplicationStatus.PENDING))
const decided = computed(() => applications.value.filter(a => a.status !== ClusterApplicationStatus.PENDING))

const {isOpen: showDeny, target: denyTarget, open: openDeny} = useModalTarget<ClusterApplication>(() => {
  denyReason.value = ''
})

async function approve(application: ClusterApplication) {
  await runWith(async () => {
    await clusterStations.decideApplication(application.id, true)
    return clusterStations.listApplications()
  }, {busy})
}

async function submitDeny() {
  const target = denyTarget.value
  if (!target) return
  await runWith(async () => {
    await clusterStations.decideApplication(target.id, false, denyReason.value)
    showDeny.value = false
    return clusterStations.listApplications()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-applications.subtitle')" :title="t('pages.cluster-applications.title')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="applications.length === 0">{{ t('clusterApplications.empty') }}</EmptyState>

        <div v-if="pending.length > 0" class="space-y-2">
          <NeutralContainer
              v-for="application in pending"
              :key="application.id"
              class="flex flex-wrap items-center justify-between gap-3"
          >
            <div>
              <p class="font-medium">{{ application.stationName }}</p>
              <p class="text-sm text-(--text-muted)">
                {{ t('clusterApplications.askedOn', {date: formatDate(application.requestedAt)}) }}
              </p>
            </div>
            <div class="flex gap-2">
              <PrimaryButton :disabled="busy" @click="approve(application)">
                {{ t('clusterApplications.approve') }}
              </PrimaryButton>
              <SecondaryButton :disabled="busy" @click="openDeny(application)">
                {{ t('clusterApplications.deny') }}
              </SecondaryButton>
            </div>
          </NeutralContainer>
        </div>

        <div v-if="decided.length > 0" class="space-y-2">
          <NeutralContainer
              v-for="application in decided"
              :key="application.id"
              class="flex flex-wrap items-center justify-between gap-3"
          >
            <div>
              <p class="font-medium">{{ application.stationName }}</p>
              <p v-if="application.denyReason" class="text-sm text-(--text-muted)">{{ application.denyReason }}</p>
            </div>
            <SecondaryBadge>{{ t(`clusterApplications.status.${application.status}`) }}</SecondaryBadge>
          </NeutralContainer>
        </div>
      </template>
    </div>

    <SingleFieldModal
        v-model:show="showDeny"
        v-model:value="denyReason"
        :confirm-label="t('clusterApplications.deny')"
        :placeholder="t('clusterApplications.denyReasonPlaceholder')"
        :title="t('clusterApplications.denyTitle')"
        @confirm="submitDeny"
    />
  </ViewContent>
</template>
