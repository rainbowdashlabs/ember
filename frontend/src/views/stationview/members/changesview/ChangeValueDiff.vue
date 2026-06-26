/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MutedIcon from '@/components/display/MutedIcon.vue'

defineProps<{
  oldValue?: string
  newValue?: string
}>()

function format(val?: string): string {
  if (!val) return '–'
  try { return JSON.parse(val) } catch { return val }
}
</script>

<template>
  <div class="flex items-center gap-2 text-xs">
    <span class="text-(--text-muted)">{{ format(oldValue) }}</span>
    <MutedIcon :icon="['fas', 'chevron-right']"/>
    <span class="font-medium">{{ format(newValue) }}</span>
  </div>
</template>
