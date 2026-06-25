/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  title: string
  count: number
}>()
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-2">
      <SubHeader>{{ title }}</SubHeader>
      <span class="text-sm text-(--text-muted)">{{ count }}</span>
    </div>
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
      <slot/>
    </div>
  </div>
</template>
