/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import FormerMembersTable from './formerview/FormerMembersTable.vue'
import ReactivateModal from './formerview/ReactivateModal.vue'
import { memberDisplayName } from './listview/useMemberData'
import type { MemberWithName } from '@/api/generated/schema'
import { stationMembers } from '@/api'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { useConfirmAction } from '@/composables/useConfirmAction'

const { t } = useI18n()

const { config: members, loading, failure, reload: loadData } = useConfigPanel<MemberWithName[]>({
  initial: [],
  fetch: () => stationMembers.listFormerMembers(),
})
const success = ref('')

const {
  show: showReactivateModal,
  target: reactivateTarget,
  request: openReactivate,
  confirm: confirmReactivate,
} = useConfirmAction<MemberWithName>({
  onConfirm: async m => { await stationMembers.reactivateMember(m.id) },
  onSuccess: async () => {
    success.value = t('formerMembers.reactivated')
    await loadData()
  },
  failure,
})
</script>

<template>
  <ViewContent
      :title="t('pages.members-former.title')"
      :subtitle="t('pages.members-former.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="failure"/>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="members.length === 0">{{ t('formerMembers.empty') }}</EmptyState>

        <FormerMembersTable
          v-if="members.length > 0"
          :members="members"
          @reactivate="openReactivate"
        />

        <p v-if="members.length > 0" class="text-xs text-(--text-muted)">
          {{ members.length }} {{ t('formerMembers.count') }}
        </p>
      </template>

      <ReactivateModal
        v-model="showReactivateModal"
        :target-name="reactivateTarget ? memberDisplayName(reactivateTarget) : ''"
        @confirm="confirmReactivate"
      />
    </div>
  </ViewContent>
</template>
