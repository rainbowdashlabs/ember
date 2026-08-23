/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {StorageRoomRow} from '@/composables/useStorageQuotas'
import {formatBytes} from '@/util/storage'

defineProps<{
  station: StorageRoomRow
  categoryColorMap: Record<string, string>
  categoryLabel: (cat: string) => string
}>()
</script>

<template>
  <div v-if="!station.usesOwnBackend" class="flex items-center gap-2">
    <div class="flex-1 bg-(--bg-muted) rounded-full h-3 overflow-hidden flex">
      <div v-for="cat in station.categories.filter(c => c.category !== 'IMAGE_AVATAR' && c.totalBytes > 0)" :key="cat.category"
           :style="{width: (station.quotaBytes > 0 ? cat.totalBytes / station.quotaBytes * 100 : 0) + '%', backgroundColor: categoryColorMap[cat.category] || '#9ca3af'}"
           :title="categoryLabel(cat.category) + ': ' + formatBytes(cat.totalBytes)"
           class="h-full first:rounded-l-full last:rounded-r-full"/>
    </div>
    <span class="text-xs whitespace-nowrap text-(--text-muted)">{{ formatBytes(station.totalBytes) }}</span>
  </div>
  <span v-else class="text-xs text-(--text-muted)">{{ formatBytes(station.totalBytes) }}</span>
</template>
