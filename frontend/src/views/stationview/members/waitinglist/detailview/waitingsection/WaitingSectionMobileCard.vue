/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import WaitingListStatusBadge from '@/components/badge/WaitingListStatusBadge.vue'
import WaitingSectionActions from './WaitingSectionActions.vue'
import WaitingSectionGuardians from './WaitingSectionGuardians.vue'
import type { WaitingListEntryWithScore } from '@/api/types'

const props = defineProps<{
  item: WaitingListEntryWithScore
  index: number
  expanded: boolean
  readonly?: boolean
}>()

const emit = defineEmits<{
  toggleExpand: [entryId: number]
  invite: [entryId: number]
  moveToTesting: [entryId: number]
  navigateToEntry: [entryId: number]
  deleteEntry: [entry: WaitingListEntryWithScore]
}>()

const { t } = useI18n()

function entryFullName(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: '2-digit' })
}
</script>

<template>
  <NeutralContainer class="space-y-2 cursor-pointer" @click="emit('toggleExpand', props.item.entry.id)">
    <div class="flex items-center justify-between">
      <div>
        <span class="text-xs text-(--text-muted) mr-2">#{{ props.index + 1 }}</span>
        <span class="font-semibold text-primary hover:underline cursor-pointer" role="link" tabindex="0" @click.stop="emit('navigateToEntry', props.item.entry.id)" @keydown.enter="emit('navigateToEntry', props.item.entry.id)">
          {{ entryFullName(props.item) }}
        </span>
      </div>
      <WaitingListStatusBadge :status="props.item.entry.status" />
    </div>
    <div class="text-xs text-(--text-muted)">{{ t('waitingList.createdAt') }}: {{ formatDate(props.item.entry.createdAt) }}</div>
    <div v-if="props.expanded" class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-2">
      <WaitingSectionGuardians :item="props.item" layout="stack" />
    </div>
    <div class="flex items-center justify-between text-sm">
      <span>{{ t('waitingList.score') }}: <span class="font-mono font-medium">{{ props.item.score }}</span></span>
      <WaitingSectionActions
        v-if="!props.readonly"
        :item="props.item"
        @invite="(id) => emit('invite', id)"
        @move-to-testing="(id) => emit('moveToTesting', id)"
        @navigate-to-entry="(id) => emit('navigateToEntry', id)"
        @delete-entry="(entry) => emit('deleteEntry', entry)"
      />
    </div>
  </NeutralContainer>
</template>
