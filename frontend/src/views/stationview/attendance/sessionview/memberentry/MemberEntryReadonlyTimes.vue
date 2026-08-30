/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {AttendanceEntry} from '@/api/attendance'
import {formatTime} from '@/util/format'

/**
 * When somebody came and went, on a sheet that can no longer be written on.
 *
 * <p>An entry with no times of its own falls back to the session's, the same way the editable sheet
 * and the report do, so a member who was simply there reads as there rather than as blank.
 */
const props = defineProps<{
  entry: AttendanceEntry
  sessionStart?: string
  sessionEnd?: string
}>()

const shownCheckIn = computed(() => formatTime(props.entry.checkIn) || formatTime(props.sessionStart))
const shownCheckOut = computed(() => formatTime(props.entry.checkOut) || formatTime(props.sessionEnd))
</script>

<template>
  <div class="flex items-center gap-2 text-xs text-(--text-muted)">
    <span v-if="entry.status === 'PRESENT' && (shownCheckIn || shownCheckOut)">
      {{ shownCheckIn || '-' }} – {{ shownCheckOut || '-' }}
    </span>
  </div>
</template>
