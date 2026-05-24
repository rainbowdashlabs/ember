/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import type { WaitingListEntryWithScore } from '@/api/types'

const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  attendanceThreshold: number
}>()

const emit = defineEmits<{
  moveToJoined: [entryId: number]
  withdraw: [entryId: number]
  navigateToEntry: [entryId: number]
}>()

const { t } = useI18n()

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
        class="space-y-2"
        :class="{ 'ring-2 ring-success/40': item.entry.attendanceCount >= attendanceThreshold }"
      >
        <div class="flex items-center justify-between">
          <span class="font-semibold text-primary hover:underline cursor-pointer" role="link" tabindex="0" @click="emit('navigateToEntry', item.entry.id)" @keydown.enter="emit('navigateToEntry', item.entry.id)">
            {{ entryFullName(item) }}
          </span>
          <PrimaryBadge>{{ t('waitingList.status_TESTING') }}</PrimaryBadge>
        </div>
        <div class="text-sm text-(--text-muted)">
          {{ item.entry.parentName }} &middot; {{ item.entry.email }}
        </div>
        <div class="flex items-center justify-between text-sm">
          <span>
            {{ t('waitingList.attendanceCount') }}: <span class="font-mono font-medium" :class="{ 'text-success': item.entry.attendanceCount >= attendanceThreshold }">{{ item.entry.attendanceCount }} / {{ attendanceThreshold }}</span>
          </span>
          <div class="flex items-center gap-1">
            <SuccessButton @click="emit('moveToJoined', item.entry.id)">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />
              {{ t('waitingList.join') }}
            </SuccessButton>
            <ErrorButton @click="emit('withdraw', item.entry.id)">
              <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1" />
              {{ t('waitingList.withdraw') }}
            </ErrorButton>
          </div>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
