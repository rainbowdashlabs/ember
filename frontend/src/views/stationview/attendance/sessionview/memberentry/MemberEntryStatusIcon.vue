/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {AttendanceStatus} from '@/api/attendance'

defineProps<{
  status?: AttendanceStatus
}>()
</script>

<template>
  <font-awesome-icon
      v-if="status === 'PRESENT'"
      :icon="['fas', 'check']" class="h-4 w-4 text-success shrink-0"
  />
  <font-awesome-icon
      v-else-if="status === 'ABSENT'"
      :icon="['fas', 'xmark']" class="h-4 w-4 text-error shrink-0"
  />
  <font-awesome-icon
      v-else-if="status === 'DECLINED'"
      :icon="['fas', 'ban']" class="h-4 w-4 text-info shrink-0"
  />
  <font-awesome-icon
      v-else
      :icon="['fas', 'asterisk']" class="h-4 w-4 text-(--text-muted) shrink-0"
  />
</template>
