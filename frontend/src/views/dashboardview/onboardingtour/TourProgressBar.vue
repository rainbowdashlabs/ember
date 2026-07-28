/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  step: number
  total: number
}>()
</script>

<template>
  <div class="mb-3 h-1 rounded-full bg-(--border) overflow-hidden">
    <div
        class="h-full bg-primary rounded-full transition-all duration-300"
        :style="{ width: `${((step + 1) / total) * 100}%` }"
    />
  </div>
</template>
