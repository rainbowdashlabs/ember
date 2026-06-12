/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {TrackingStatusName} from '@/api/dataTracking'

const props = defineProps<{
  status: TrackingStatusName | string
  label?: string
  count?: number
}>()

const classes = computed(() => {
  switch (props.status) {
    case 'TRACKED':
      return 'bg-[#00C507]/15 text-[#00A005] border border-[#00C507]/40'
    case 'IGNORED':
      return 'bg-[#ffdd1b]/15 text-[#a07a00] border border-[#ffdd1b]/50 dark:text-[#ffdd1b]'
    case 'UNVERIFIED':
      return 'bg-[#ec2929]/15 text-[#ec2929] border border-[#ec2929]/40'
    default:
      return 'bg-(--bg-accent) text-(--text-muted) border border-(--border)'
  }
})
</script>

<template>
  <span
      class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-mono font-semibold"
      :class="classes"
  >
    <span v-if="label" class="opacity-70">{{ label }}:</span>
    <span>{{ status }}</span>
    <span v-if="count !== undefined" class="ml-1 opacity-80">({{ count }})</span>
  </span>
</template>
