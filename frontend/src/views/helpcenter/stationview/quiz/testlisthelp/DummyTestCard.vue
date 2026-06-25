/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'

defineProps<{
  name: string
  description: string
  attempts: string
}>()
</script>

<template>
  <NeutralContainer class="cursor-pointer">
    <div class="flex items-center justify-between gap-4">
      <div class="flex-1 space-y-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ name }}</span>
          <slot name="badges" />
        </div>
        <p class="text-xs text-(--text-muted) line-clamp-1">{{ description }}</p>
      </div>
      <div class="flex items-center gap-4 text-xs text-(--text-muted) shrink-0">
        <span>{{ attempts }}</span>
        <slot name="actions" />
      </div>
    </div>
  </NeutralContainer>
</template>
