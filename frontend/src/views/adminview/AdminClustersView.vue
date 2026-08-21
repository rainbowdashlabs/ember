/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
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
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {clusters} from '@/api'
import type {AccountSearchResult} from '@/api/twoFactorAdmin'
import ClusterRow from './adminclustersview/ClusterRow.vue'
import type {Cluster} from '@/api/clusters'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useModalTarget} from '@/composables/useModalTarget'

const {t} = useI18n()

const newName = ref('')
const newDescription = ref('')
const busy = ref(false)

const {config: clusterList, loading, error, runWith} = useConfigPanel<Cluster[]>({
  initial: [],
  fetch: () => clusters.listAll(),
})

const {isOpen: showDelete, target: deleteTarget, open: openDelete} = useModalTarget<Cluster>()

async function appoint(cluster: Cluster, account: AccountSearchResult) {
  await runWith(async () => {
    await clusters.appointAdministrator(cluster.uid, account.uid)
    return clusters.listAll()
  }, {busy})
}

async function create() {
  if (!newName.value.trim()) return
  await runWith(async () => {
    await clusters.createCluster({
      name: newName.value.trim(),
      description: newDescription.value.trim() || null,
    })
    newName.value = ''
    newDescription.value = ''
    return clusters.listAll()
  }, {busy})
}

async function confirmDelete() {
  const target = deleteTarget.value
  if (!target) return
  await runWith(async () => {
    await clusters.deleteCluster(target.uid)
    showDelete.value = false
    return clusters.listAll()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.admin-clusters.subtitle')" :title="t('pages.admin-clusters.title')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('adminClusters.createTitle') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('adminClusters.createHint') }}</p>

        <div class="space-y-1">
          <FormLabel>{{ t('adminClusters.nameLabel') }}</FormLabel>
          <TextInput v-model="newName" :placeholder="t('adminClusters.namePlaceholder')"/>
        </div>

        <div class="space-y-1">
          <FormLabel>{{ t('adminClusters.descriptionLabel') }}</FormLabel>
          <TextAreaInput v-model="newDescription" :placeholder="t('adminClusters.descriptionPlaceholder')"/>
        </div>

        <PrimaryButton :disabled="busy || !newName.trim()" @click="create">
          {{ t('common.create') }}
        </PrimaryButton>
      </NeutralContainer>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="clusterList.length === 0">{{ t('adminClusters.empty') }}</EmptyState>
        <div v-else class="space-y-2">
          <ClusterRow
              v-for="cluster in clusterList"
              :key="cluster.uid"
              :busy="busy"
              :cluster="cluster"
              @appoint="appoint"
              @remove="openDelete"
          />
        </div>
      </template>
    </div>

    <ConfirmDeleteModal
        v-model="showDelete"
        :message="t('adminClusters.deleteMessage', {name: deleteTarget?.name ?? ''})"
        @confirm="confirmDelete"
    />
  </ViewContent>
</template>
