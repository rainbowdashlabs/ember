/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import type { WaitingListEntryWithScore } from '@/api/waitingList'
import { formatDate } from '@/util/format'

const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  attendanceThreshold: number
  readonly?: boolean
}>()

const emit = defineEmits<{
  moveToJoined: [entryId: number]
  withdraw: [entryId: number]
  navigateToEntry: [entryId: number]
}>()

const { t } = useI18n()
const expandedId = ref<number | null>(null)

function toggleExpand(entryId: number) {
  expandedId.value = expandedId.value === entryId ? null : entryId
}

function entryFullName(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('waitingList.sectionTesting') }} ({{ entries.length }})</SubHeader>

    <EmptyState compact v-if="entries.length === 0">{{ t('waitingList.noTestingEntries') }}</EmptyState>

    <div v-if="entries.length > 0" class="space-y-3">
      <NeutralContainer
        v-for="item in entries"
        :key="item.entry.id"
        class="space-y-2 cursor-pointer"
        :class="{ 'ring-2 ring-success/40': item.entry.attendanceCount >= attendanceThreshold }"
        @click="toggleExpand(item.entry.id)"
      >
        <div class="flex items-center justify-between">
          <span class="font-semibold text-primary hover:underline cursor-pointer" role="link" tabindex="0" @click.stop="emit('navigateToEntry', item.entry.id)" @keydown.enter="emit('navigateToEntry', item.entry.id)">
            {{ entryFullName(item) }}
          </span>
          <PrimaryBadge>{{ t('waitingList.status_TESTING') }}</PrimaryBadge>
        </div>
        <div class="text-xs text-(--text-muted)">{{ t('waitingList.createdAt') }}: {{ formatDate(item.entry.createdAt) }}</div>
        <div v-if="expandedId === item.entry.id" class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-2 space-y-1">
          <template v-if="item.guardians && item.guardians.length > 0">
            <span class="text-xs font-semibold uppercase text-(--text-muted)">{{ t('waitingList.guardians') }}</span>
            <div v-for="g in item.guardians" :key="g.id" class="text-sm flex flex-col">
              <span class="font-medium">{{ `${g.firstname} ${g.lastname}`.trim() || '-' }}</span>
              <span class="text-(--text-muted)">{{ g.email }}{{ g.phone ? ` · ${g.phone}` : '' }}</span>
            </div>
          </template>
          <span v-else class="text-sm text-(--text-muted)">{{ t('waitingList.noGuardians') }}</span>
        </div>
        <div class="flex items-center justify-between text-sm">
          <span>
            {{ t('waitingList.attendanceCount') }}: <span class="font-mono font-medium" :class="{ 'text-success': item.entry.attendanceCount >= attendanceThreshold }">{{ item.entry.attendanceCount }} / {{ attendanceThreshold }}</span>
          </span>
          <div v-if="!readonly" class="flex items-center gap-1">
            <SuccessButton :icon="['fas', 'check']" @click.stop="emit('moveToJoined', item.entry.id)">
              {{ t('waitingList.join') }}
            </SuccessButton>
            <ErrorButton :icon="['fas', 'xmark']" @click.stop="emit('withdraw', item.entry.id)">
              {{ t('waitingList.withdraw') }}
            </ErrorButton>
          </div>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
