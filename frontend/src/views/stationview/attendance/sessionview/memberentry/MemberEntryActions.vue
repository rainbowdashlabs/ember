/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MemberEntryStatusButtons from './MemberEntryStatusButtons.vue'
import MemberEntryTimeRange from './MemberEntryTimeRange.vue'
import type {AttendanceStatus} from '@/api/attendance'
import type {AttendanceEntry} from '@/api/generated/schema'

defineProps<{
  entry: AttendanceEntry
  sessionStart?: string
  sessionEnd?: string
  spansDays?: boolean
}>()

const emit = defineEmits<{
  setStatus: [status: AttendanceStatus]
  checkIn: [time: string]
  checkOut: [time: string]
  resetTimes: []
}>()
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center gap-2">
    <MemberEntryStatusButtons :status="entry.status" @set-status="emit('setStatus', $event)"/>
    <MemberEntryTimeRange
        v-if="entry.status === 'PRESENT'"
        :check-in="entry.checkIn ?? undefined"
        :check-out="entry.checkOut ?? undefined"
        :session-end="sessionEnd"
        :session-start="sessionStart"
        :spans-days="spansDays"
        @check-in="emit('checkIn', $event)"
        @check-out="emit('checkOut', $event)"
        @reset-times="emit('resetTimes')"
    />
  </div>
</template>
