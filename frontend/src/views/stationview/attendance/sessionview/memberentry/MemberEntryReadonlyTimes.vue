/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {AttendanceEntry} from '@/api/attendance'
import {formatDayMonth, formatTime} from '@/util/format'

/**
 * When somebody came and went, on a sheet that can no longer be written on.
 *
 * <p>An entry with no times of its own falls back to the session's, the same way the editable sheet
 * and the report do, so a member who was simply there reads as there rather than as blank. Where the
 * sheet runs over more than one day, each moment carries the day it happened on.
 */
const props = defineProps<{
  entry: AttendanceEntry
  sessionStart?: string
  sessionEnd?: string
  spansDays?: boolean
}>()

const shownCheckIn = computed(() => shown(props.entry.checkIn, props.sessionStart))
const shownCheckOut = computed(() => shown(props.entry.checkOut, props.sessionEnd))

function shown(own?: string | null, session?: string): string {
  const moment = own || session
  if (!moment) return ''
  return props.spansDays ? `${formatDayMonth(moment)} ${formatTime(moment)}` : formatTime(moment)
}
</script>

<template>
  <div class="flex items-center gap-2 text-xs text-(--text-muted)">
    <span v-if="entry.status === 'PRESENT' && (shownCheckIn || shownCheckOut)">
      {{ shownCheckIn || '-' }} – {{ shownCheckOut || '-' }}
    </span>
  </div>
</template>
