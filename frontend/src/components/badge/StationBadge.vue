/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'

defineProps<{
  stationName: string
}>()
</script>

<template>
  <BaseBadge bg-class="bg-secondary/70 border border-secondary/30" class="gap-1 text-[10px]">
    <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-2.5 h-2.5" />
    {{ stationName }}
  </BaseBadge>
</template>
