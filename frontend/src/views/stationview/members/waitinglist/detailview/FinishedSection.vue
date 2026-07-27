/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import WaitingListStatusBadge from '@/components/badge/WaitingListStatusBadge.vue'
import type { WaitingListEntryWithScore } from '@/api/types'
import { formatDate } from '@/util/format'

const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  canLinkMember?: boolean
}>()

const emit = defineEmits<{
  navigateToEntry: [entryId: number]
  navigateToMember: [memberId: number]
}>()

const { t } = useI18n()

function entryFullName(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}

function canNavigateToMember(item: WaitingListEntryWithScore): boolean {
  return item.entry.status === 'JOINED' && !!item.entry.memberId && !!props.canLinkMember
}

function onNameClick(item: WaitingListEntryWithScore) {
  if (canNavigateToMember(item)) {
    emit('navigateToMember', item.entry.memberId!)
  } else {
    emit('navigateToEntry', item.entry.id)
  }
}

function finishedDate(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return formatDate(e.status === 'JOINED' ? e.joinedAt : e.withdrawnAt) || '-'
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('waitingList.sectionFinished') }} ({{ entries.length }})</SubHeader>

    <EmptyState compact v-if="entries.length === 0">{{ t('waitingList.noFinishedEntries') }}</EmptyState>

    <div v-if="entries.length > 0" class="space-y-2">
      <div
        v-for="item in entries"
        :key="item.entry.id"
        class="flex items-center justify-between gap-2 px-3 py-2 rounded-lg bg-bg-light-accent/20 dark:bg-bg-dark-accent/20"
      >
        <div class="flex items-center gap-2">
          <span
            class="font-medium"
            :class="canNavigateToMember(item) ? 'text-primary hover:underline cursor-pointer' : ''"
            role="link" tabindex="0"
            @click="canNavigateToMember(item) && onNameClick(item)"
            @keydown.enter="canNavigateToMember(item) && onNameClick(item)"
          >
            {{ entryFullName(item) }}
          </span>
          <WaitingListStatusBadge :status="item.entry.status" />
        </div>
        <span class="text-xs text-(--text-muted)">
          {{ finishedDate(item) }}
        </span>
      </div>
    </div>
  </NeutralContainer>
</template>
