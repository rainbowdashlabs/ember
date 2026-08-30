/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'
import MemberEntryStatusIcon from './memberentry/MemberEntryStatusIcon.vue'
import MemberEntryActions from './memberentry/MemberEntryActions.vue'
import MemberEntryReadonlyTimes from './memberentry/MemberEntryReadonlyTimes.vue'
import type {AttendanceEntry, AttendanceStatus} from '@/api/attendance'
import type {StationMember} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  member: StationMember
  entry?: AttendanceEntry
  memberName: string
  readonly?: boolean
  sessionStart?: string
  sessionEnd?: string
}>()

const emit = defineEmits<{
  setStatus: [entryId: number, status: AttendanceStatus]
  checkIn: [entryId: number, time: string]
  checkOut: [entryId: number, time: string]
  resetTimes: [entryId: number]
}>()
</script>

<template>
  <div
      :class="{
        'border-success bg-success/5': entry?.status === 'PRESENT',
        'border-error bg-error/5': entry?.status === 'ABSENT',
        'border-info bg-info/5': entry?.status === 'DECLINED',
        'border-bg-light-accent dark:border-bg-dark-accent bg-bg-light-accent/20 dark:bg-bg-dark-accent/20': !entry || entry?.status === 'UNCONFIRMED',
      }"
      class="rounded-lg px-4 py-3 border-l-4 transition-all"
  >
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div class="flex items-center gap-2 min-w-0">
        <MemberEntryStatusIcon :status="entry?.status"/>
        <MemberName :identity="member.identity" class="font-medium text-sm truncate"/>
      </div>
      <MemberEntryActions
          v-if="entry && !readonly"
          :entry="entry"
          :session-end="sessionEnd"
          :session-start="sessionStart"
          @set-status="emit('setStatus', entry.id, $event)"
          @check-in="emit('checkIn', entry.id, $event)"
          @check-out="emit('checkOut', entry.id, $event)"
          @reset-times="emit('resetTimes', entry.id)"
      />
      <MemberEntryReadonlyTimes
          v-else-if="entry && readonly"
          :entry="entry"
          :session-end="sessionEnd"
          :session-start="sessionStart"
      />
      <span v-else class="text-xs text-(--text-muted)">{{ t('attendanceSession.noEntry') }}</span>
    </div>
  </div>
</template>
