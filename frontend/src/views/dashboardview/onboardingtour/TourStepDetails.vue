/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  icon: string | string[]
  title: string
  body: string
}>()
</script>

<template>
  <div class="hidden sm:flex items-center justify-center h-10 w-10 shrink-0 rounded-full bg-primary/10">
    <font-awesome-icon :icon="icon" class="h-5 w-5 text-primary"/>
  </div>

  <div class="flex-1 min-w-0">
    <SubHeader class="text-sm font-bold">
      {{ title }}
    </SubHeader>
    <p class="text-xs text-(--text-muted) mt-0.5 leading-relaxed">
      {{ body }}
    </p>
  </div>
</template>
