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
import SelectInput from '@/components/input/select/SelectInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import RosterPanel from './clustermembersview/RosterPanel.vue'
import AddMemberModal from './clustermembersview/AddMemberModal.vue'
import MemberEditor from './clustermembersview/MemberEditor.vue'
import {clusterMembers} from '@/api'
import type {ClusterMemberSummary} from '@/api/clusterMembers'
import {ClusterPermission, ClusterUserType} from '@/api/clusters'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useSession} from '@/composables/useSession'
import {apiErrorBody} from '@/util/apiError'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

const editable = computed(() => hasClusterPermission(ClusterPermission.CLUSTER_ADMINISTRATOR))

const busy = ref(false)
const showAdd = ref(false)
/** True once the server has said the address in the dialog has no account behind it. */
const needsName = ref(false)
const selectedId = ref<number | null>(null)

const {config: members, loading, error, runWith} = useConfigPanel<ClusterMemberSummary[]>({
  initial: [],
  fetch: () => clusterMembers.listMembers(),
})

const selected = computed(() => members.value.find(m => m.id === selectedId.value) ?? null)

/**
 * Takes somebody on, asking for a name only when the server says the address has no account.
 *
 * <p>Two attempts rather than a lookup first: the association learns that nobody has that address by
 * trying to add them, which is what it was doing anyway, and no endpoint exists that would answer the
 * question for an address they never meant to use.
 */
async function add(email: string, userType: string, firstName: string, lastName: string) {
  await runWith(async () => {
    try {
      await clusterMembers.addMember(email, userType, firstName && lastName ? {firstName, lastName} : undefined)
    } catch (e: unknown) {
      if (apiErrorBody(e)?.error === clusterMembers.ACCOUNT_NAME_REQUIRED) {
        needsName.value = true
        return members.value
      }
      throw e
    }
    needsName.value = false
    showAdd.value = false
    return clusterMembers.listMembers()
  }, {busy})
}

async function remove(memberId: number) {
  await runWith(async () => {
    await clusterMembers.removeMember(memberId)
    if (selectedId.value === memberId) selectedId.value = null
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
      <Spinner v-if="loading" size="lg"/>

      <div v-else class="grid gap-6 lg:grid-cols-2">
        <RosterPanel
            :members="members"
            :selected-id="selectedId"
            :editable="editable"
            @add="showAdd = true"
            @select="member => selectedId = member.id"
        />

        <NeutralContainer v-if="selected" class="space-y-4">
          <div class="flex items-center justify-between gap-3">
            <SectionHeader>{{ selected.name ?? selected.email }}</SectionHeader>
            <DeleteButton v-if="editable" :disabled="busy" @click="remove(selected.id)"/>
          </div>

          <div class="space-y-1">
            <FormLabel>{{ t('clusterMembers.userTypeLabel') }}</FormLabel>
            <SelectInput
                :disabled="!editable || busy"
                :model-value="selected.userType"
                @update:model-value="v => changeUserType(selected!.id, String(v))"
            >
              <option :value="ClusterUserType.CLUSTER_USER">{{ t('clusterOverview.role.CLUSTER_USER') }}</option>
              <option :value="ClusterUserType.CLUSTER_ADMIN">{{ t('clusterOverview.role.CLUSTER_ADMIN') }}</option>
            </SelectInput>
          </div>

          <MemberEditor :member-id="selected.id" :editable="editable" @saved="reload"/>
        </NeutralContainer>

        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          {{ t('clusterMembers.selectHint') }}
        </div>
      </div>

      <AddMemberModal v-model="showAdd" :saving="busy" :needs-name="needsName" @add="add"/>
    </div>
  </ViewContent>
</template>
