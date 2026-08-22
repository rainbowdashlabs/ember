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
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ClusterMemberRow from './clustermembersview/ClusterMemberRow.vue'
import {clusterMembers} from '@/api'
import type {ClusterMemberSummary} from '@/api/clusterMembers'
import {ClusterUserType} from '@/api/clusters'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useSession} from '@/composables/useSession'
import {ClusterPermission} from '@/api/clusters'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

const newEmail = ref('')
const newUserType = ref<string>(ClusterUserType.CLUSTER_USER)
const busy = ref(false)
const openMemberId = ref<number | null>(null)

const {config: members, loading, error, runWith} = useConfigPanel<ClusterMemberSummary[]>({
  initial: [],
  fetch: () => clusterMembers.listMembers(),
})

async function add() {
  if (!newEmail.value.trim()) return
  await runWith(async () => {
    await clusterMembers.addMember(newEmail.value.trim(), newUserType.value)
    newEmail.value = ''
    return clusterMembers.listMembers()
  }, {busy})
}

async function remove(memberId: number) {
  await runWith(async () => {
    await clusterMembers.removeMember(memberId)
    if (openMemberId.value === memberId) openMemberId.value = null
    return clusterMembers.listMembers()
  }, {busy})
}

async function reload() {
  await runWith(() => clusterMembers.listMembers(), {busy})
}

async function changeUserType(memberId: number, userType: string) {
  await runWith(async () => {
    await clusterMembers.setMemberUserType(memberId, userType)
    return clusterMembers.listMembers()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-members.subtitle')" :title="t('pages.cluster-members.title')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer v-if="hasClusterPermission(ClusterPermission.CLUSTER_ADMINISTRATOR)" class="space-y-4">
        <SectionHeader>{{ t('clusterMembers.addTitle') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('clusterMembers.addHint') }}</p>

        <div class="space-y-1">
          <FormLabel>{{ t('clusterMembers.emailLabel') }}</FormLabel>
          <TextInput v-model="newEmail" :placeholder="t('clusterMembers.emailPlaceholder')"/>
        </div>

        <div class="space-y-1">
          <FormLabel>{{ t('clusterMembers.userTypeLabel') }}</FormLabel>
          <SelectInput v-model="newUserType">
            <option :value="ClusterUserType.CLUSTER_USER">{{ t('clusterOverview.role.CLUSTER_USER') }}</option>
            <option :value="ClusterUserType.CLUSTER_ADMIN">{{ t('clusterOverview.role.CLUSTER_ADMIN') }}</option>
          </SelectInput>
        </div>

        <PrimaryButton :disabled="busy || !newEmail.trim()" @click="add">{{ t('common.add') }}</PrimaryButton>
      </NeutralContainer>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="members.length === 0">{{ t('clusterMembers.empty') }}</EmptyState>
        <div v-else class="space-y-2">
          <ClusterMemberRow
              v-for="member in members"
              :key="member.id"
              :busy="busy"
              :editable="hasClusterPermission(ClusterPermission.CLUSTER_ADMINISTRATOR)"
              :member="member"
              :open="openMemberId === member.id"
              @remove="remove"
              @saved="reload"
              @toggle="id => openMemberId = openMemberId === id ? null : id"
              @user-type="changeUserType"
          />
        </div>
      </template>
    </div>
  </ViewContent>
</template>
