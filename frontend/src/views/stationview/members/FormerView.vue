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
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import FormerMembersTable from './formerview/FormerMembersTable.vue'
import ReactivateModal from './formerview/ReactivateModal.vue'
import type { StationMember } from '@/api/types'
import { stationMembers } from '@/api'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { useConfirmAction } from '@/composables/useConfirmAction'

const { t } = useI18n()

const { config: members, loading, error, reload: loadData } = useConfigPanel<StationMember[]>({
  initial: [],
  fetch: () => stationMembers.listFormerMembers(),
})
const success = ref('')

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return '–'
  return new Date(dateStr).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

const {
  show: showReactivateModal,
  target: reactivateTarget,
  request: openReactivate,
  confirm: confirmReactivate,
} = useConfirmAction<StationMember>({
  onConfirm: m => stationMembers.reactivateMember(m.id),
  onSuccess: async () => {
    success.value = t('formerMembers.reactivated')
    await loadData()
  },
  error,
})
</script>

<template>
  <ViewContent
      :title="t('pages.members-former.title')"
      :subtitle="t('pages.members-former.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="members.length === 0">{{ t('formerMembers.empty') }}</EmptyState>

        <FormerMembersTable
          v-if="members.length > 0"
          :members="members"
          :member-display-name="memberDisplayName"
          :format-date="formatDate"
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
