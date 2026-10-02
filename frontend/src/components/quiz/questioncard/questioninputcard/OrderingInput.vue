/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import DragList from '@/components/input/DragList.vue'

const props = defineProps<{
  config: Record<string, unknown>
  disabled: boolean
  orderItems: number[]
}>()

const emit = defineEmits<{
  reorderItems: [fromIndex: number, toIndex: number]
}>()

const orderedItems = computed<string[]>(() => {
  const items = (props.config.items as string[]) ?? []
  if (props.orderItems.length === items.length) return props.orderItems.map(i => items[i] ?? '')
  return items
})
</script>

<template>
  <DragList
      :items="orderedItems"
      :key-fn="(_, index) => index"
      :disabled="disabled"
      class="space-y-2"
      @reorder="(from, to) => emit('reorderItems', from, to)"
  >
    <template #default="{item, index}">
      <div class="flex items-center gap-2 p-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent">
        <span class="text-xs text-(--text-muted) w-5 text-right shrink-0">{{ index + 1 }}.</span>
        <span class="flex-1 text-sm">{{ item }}</span>
      </div>
    </template>
  </DragList>
</template>
