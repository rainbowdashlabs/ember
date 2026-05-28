/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  stationName: string
}>()
</script>

<template>
  <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-secondary/10 text-secondary-badge border border-secondary/30">
    <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-2.5 h-2.5" />
    {{ stationName }}
  </span>
</template>
