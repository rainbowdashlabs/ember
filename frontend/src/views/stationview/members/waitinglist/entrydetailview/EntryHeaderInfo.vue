/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import WaitingListStatusBadge from '@/components/badge/WaitingListStatusBadge.vue'
import type { WaitingListEntryWithScore } from '@/api/types'

defineProps<{
  entry: WaitingListEntryWithScore
  entryFullName: string
}>()

const editingCreatedAt = defineModel<boolean>('editingCreatedAt', {required: true})
const editCreatedAtValue = defineModel<string>('editCreatedAtValue', {required: true})

const emit = defineEmits<{
  'start-edit-created-at': []
  'save-created-at': []
}>()

const { t } = useI18n()

function formatDateTime(dateStr: string | undefined | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}
</script>

<template>
  <div class="flex items-center gap-2">
    <SubHeader>{{ entryFullName }}</SubHeader>
    <WaitingListStatusBadge :status="entry.entry.status" />
  </div>

  <div class="text-sm text-(--text-muted) flex flex-wrap items-center gap-x-4 gap-y-2">
    <span>{{ t('waitingList.score') }}: <span class="font-mono font-medium">{{ entry.score }}</span></span>
    <span>{{ t('waitingList.confirmedAt') }}: {{ formatDateTime(entry.entry.confirmedAt) }}</span>
    <span class="inline-flex items-center gap-1">
      {{ t('waitingList.createdAt') }}:
      <template v-if="editingCreatedAt">
        <DateTimeInput v-model="editCreatedAtValue" />
        <IconButton :icon="['fas', 'check']" label="Speichern" @click="emit('save-created-at')" />
        <IconButton :icon="['fas', 'xmark']" label="Abbrechen" @click="editingCreatedAt = false" />
      </template>
      <template v-else>
        {{ formatDateTime(entry.entry.createdAt) }}
        <EditButton :label="t('common.edit')" @click="emit('start-edit-created-at')" />
      </template>
    </span>
    <span v-if="entry.entry.invitedAt">{{ t('waitingList.invitedAt') }}: {{ formatDateTime(entry.entry.invitedAt) }}</span>
    <span v-if="entry.entry.testingAt">{{ t('waitingList.testingAt') }}: {{ formatDateTime(entry.entry.testingAt) }}</span>
    <span v-if="entry.entry.joinedAt">{{ t('waitingList.joinedAt') }}: {{ formatDateTime(entry.entry.joinedAt) }}</span>
    <span v-if="entry.entry.withdrawnAt">{{ t('waitingList.withdrawnAt') }}: {{ formatDateTime(entry.entry.withdrawnAt) }}</span>
    <span v-if="entry.entry.status === 'TESTING'">{{ t('waitingList.attendanceCount') }}: <span class="font-mono font-medium">{{ entry.entry.attendanceCount }}</span></span>
  </div>
</template>
