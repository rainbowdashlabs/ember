/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/** One fact an opened problem report carries beside its text. */
interface ReportMetadataEntry {
  label: string
  value: string
}

defineProps<{entries: ReportMetadataEntry[]}>()
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs border-t border-(--border) pt-2">
    <div v-for="entry in entries" :key="entry.label">
      <span class="font-semibold text-(--text-muted)">{{ entry.label }}:</span>
      <span class="ml-1 break-all">{{ entry.value }}</span>
    </div>
  </div>
</template>
