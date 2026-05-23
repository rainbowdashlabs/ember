/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { WaitingListEntryWithScore } from '@/api/types'

defineProps<{
  entries: WaitingListEntryWithScore[]
}>()

const emit = defineEmits<{
  navigateToEntry: [entryId: number]
}>()

const { t } = useI18n()

function entryFullName(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}

function statusBadgeComponent(status: string) {
  if (status === 'JOINED') return SuccessBadge
  if (status === 'WITHDRAWN') return ErrorBadge
  if (status === 'TESTING') return PrimaryBadge
  if (status === 'INVITED') return InfoBadge
  return SecondaryBadge
}

function formatDate(dateStr: string | undefined | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString()
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('waitingList.sectionFinished') }} ({{ entries.length }})</SubHeader>

    <div v-if="entries.length === 0" class="text-center text-(--text-muted) py-4">
      {{ t('waitingList.noFinishedEntries') }}
    </div>

    <div v-if="entries.length > 0" class="space-y-2">
      <div
        v-for="item in entries"
        :key="item.entry.id"
        class="flex items-center justify-between gap-2 px-3 py-2 rounded-lg bg-bg-light-accent/20 dark:bg-bg-dark-accent/20"
      >
        <div class="flex items-center gap-3">
          <span class="font-medium text-primary hover:underline cursor-pointer" role="link" tabindex="0" @click="emit('navigateToEntry', item.entry.id)" @keydown.enter="emit('navigateToEntry', item.entry.id)">
            {{ entryFullName(item) }}
          </span>
          <component :is="statusBadgeComponent(item.entry.status)">{{ t('waitingList.status_' + item.entry.status) }}</component>
        </div>
        <span class="text-xs text-(--text-muted)">
          {{ item.entry.status === 'JOINED' ? formatDate(item.entry.joinedAt) : formatDate(item.entry.withdrawnAt) }}
        </span>
      </div>
    </div>
  </NeutralContainer>
</template>
