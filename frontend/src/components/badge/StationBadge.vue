/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import BaseBadge from './BaseBadge.vue'

const props = defineProps<{
  stationName: string
  compact?: boolean
}>()

const expanded = ref(false)
</script>

<template>
  <span class="inline-flex items-center transition-all duration-200 overflow-hidden"
        @mouseenter="compact ? expanded = true : undefined"
        @mouseleave="compact ? expanded = false : undefined"
        @touchstart.stop="compact ? expanded = !expanded : undefined">
    <BaseBadge bg-class="bg-secondary/70 border border-secondary/30" class="gap-1 text-[10px]">
      <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-2.5 h-2.5 shrink-0" />
      <span v-if="(!compact || expanded) && stationName" class="whitespace-nowrap">{{ stationName }}</span>
    </BaseBadge>
  </span>
</template>
