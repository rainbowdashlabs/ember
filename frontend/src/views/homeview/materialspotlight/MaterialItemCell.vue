/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SizeBadge from '@/components/badge/SizeBadge.vue'

defineProps<{
  name: string
  size: string
}>()
</script>

<template>
  <div class="cell-item">
    <span class="name">{{ name }}</span>
    <SizeBadge>{{ size }}</SizeBadge>
  </div>
</template>

<style scoped>
.cell-item {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}
.cell-item .name {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
</style>
