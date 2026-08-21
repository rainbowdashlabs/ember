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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {clusters} from '@/api'
import {searchAccounts, type AccountSearchResult} from '@/api/twoFactorAdmin'
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

// Which cluster's appoint box is open, and what has been typed into it. One at a time, because a cluster
// with nobody in it is a thing an administrator deals with once and then never looks at again.
const appointing = ref<string | null>(null)
const appointQuery = ref('')
const appointResults = ref<AccountSearchResult[]>([])

function startAppointing(uid: string) {
  appointing.value = appointing.value === uid ? null : uid
  appointQuery.value = ''
  appointResults.value = []
}

async function findAccounts() {
  if (appointQuery.value.trim().length < 2) {
    appointResults.value = []
    return
  }
  appointResults.value = await searchAccounts(appointQuery.value.trim(), 10)
}

async function appoint(cluster: Cluster, account: AccountSearchResult) {
  await runWith(async () => {
    await clusters.appointAdministrator(cluster.uid, account.uid)
    appointing.value = null
    appointQuery.value = ''
    appointResults.value = []
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
          <NeutralContainer v-for="cluster in clusterList" :key="cluster.uid" class="space-y-3">
            <div class="flex items-center justify-between gap-4">
              <div>
                <p class="font-medium">{{ cluster.name }}</p>
                <p v-if="cluster.description" class="text-sm text-(--text-muted)">{{ cluster.description }}</p>
              </div>
              <div class="flex items-center gap-2">
                <SecondaryButton :disabled="busy" @click="startAppointing(cluster.uid)">
                  {{ t('adminClusters.appoint') }}
                </SecondaryButton>
                <DeleteButton :disabled="busy" @click="openDelete(cluster)"/>
              </div>
            </div>

            <div v-if="appointing === cluster.uid" class="space-y-2">
              <p class="text-sm text-(--text-muted)">{{ t('adminClusters.appointHint') }}</p>
              <TextInput
                  v-model="appointQuery"
                  :placeholder="t('adminClusters.appointPlaceholder')"
                  @input="findAccounts"
              />
              <div v-if="appointResults.length" class="space-y-1">
                <button
                    v-for="account in appointResults"
                    :key="account.uid"
                    class="block w-full rounded px-2 py-1 text-left hover:bg-(--surface-hover)"
                    type="button"
                    @click="appoint(cluster, account)"
                >
                  {{ account.displayName }} <span class="text-(--text-muted)">{{ account.email }}</span>
                </button>
              </div>
            </div>
          </NeutralContainer>
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
