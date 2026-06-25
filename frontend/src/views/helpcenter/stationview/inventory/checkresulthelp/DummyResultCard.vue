/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'

defineProps<{
  variant: 'success' | 'error' | 'info'
  name: string
  size: string
  category: string
  inventoryId: string
  note?: string
}>()
</script>

<template>
  <NeutralContainer
    class="border-l-4"
    :class="{
      'bg-success/10 border-success': variant === 'success',
      'bg-error/10 border-error': variant === 'error',
      'bg-info/10 border-info': variant === 'info',
    }"
  >
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div class="flex-1 min-w-0">
        <div class="font-medium text-sm">{{ name }} <span class="font-normal text-(--text-muted)">[{{ size }}]</span></div>
        <div class="text-xs text-(--text-muted)">{{ category }} &middot; {{ inventoryId }}</div>
      </div>
      <slot/>
    </div>
    <p v-if="note" class="text-(--text-muted) text-sm mt-1">{{ note }}</p>
  </NeutralContainer>
</template>
