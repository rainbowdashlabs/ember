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
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import GroupEditor from './clustermembergroupsview/GroupEditor.vue'
import {clusterMembers} from '@/api'
import type {ClusterGroupSummary} from '@/api/clusterMembers'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useSession} from '@/composables/useSession'
import {ClusterPermission} from '@/api/clusters'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

const newName = ref('')
const busy = ref(false)
const openGroupId = ref<number | null>(null)

const {config: groups, loading, error, runWith} = useConfigPanel<ClusterGroupSummary[]>({
  initial: [],
  fetch: () => clusterMembers.listGroups(),
})

const editable = hasClusterPermission(ClusterPermission.CLUSTER_ADMINISTRATOR)

async function create() {
  if (!newName.value.trim()) return
  await runWith(async () => {
    await clusterMembers.createGroup(newName.value.trim())
    newName.value = ''
    return clusterMembers.listGroups()
  }, {busy})
}

async function remove(groupId: number) {
  await runWith(async () => {
    await clusterMembers.deleteGroup(groupId)
    if (openGroupId.value === groupId) openGroupId.value = null
    return clusterMembers.listGroups()
  }, {busy})
}

async function reload() {
  await runWith(() => clusterMembers.listGroups(), {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-member-groups.subtitle')" :title="t('pages.cluster-member-groups.title')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer v-if="editable" class="space-y-3">
        <SectionHeader>{{ t('clusterMemberGroups.createTitle') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('clusterMemberGroups.createHint') }}</p>
        <div class="flex items-center gap-2">
          <TextInput v-model="newName" :placeholder="t('clusterMemberGroups.namePlaceholder')"/>
          <PrimaryButton :disabled="busy || !newName.trim()" @click="create">{{ t('common.create') }}</PrimaryButton>
        </div>
      </NeutralContainer>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="groups.length === 0">{{ t('clusterMemberGroups.empty') }}</EmptyState>
        <div v-else class="space-y-2">
          <NeutralContainer v-for="group in groups" :key="group.id" class="space-y-3">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <span class="font-medium">{{ group.name }}</span>
              <div class="flex items-center gap-2">
                <SecondaryButton
                    @click="openGroupId = openGroupId === group.id ? null : group.id"
                >
                  {{ openGroupId === group.id ? t('common.close') : t('common.edit') }}
                </SecondaryButton>
                <DeleteButton v-if="editable" :disabled="busy" @click="remove(group.id)"/>
              </div>
            </div>

            <GroupEditor v-if="openGroupId === group.id" :editable="editable" :group-id="group.id" @saved="reload"/>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
