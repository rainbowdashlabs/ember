/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {AttendanceEntry} from '@/api/attendance'
import {formatTime} from '@/util/format'

defineProps<{
  entry: AttendanceEntry
}>()
</script>

<template>
  <div class="flex items-center gap-2 text-xs text-(--text-muted)">
    <span v-if="entry.status === 'PRESENT' && (entry.checkIn || entry.checkOut)">
      {{ formatTime(entry.checkIn) || '-' }} – {{ formatTime(entry.checkOut) || '-' }}
    </span>
  </div>
</template>
