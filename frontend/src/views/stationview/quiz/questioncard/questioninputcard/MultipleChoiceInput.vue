/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  config: Record<string, unknown>
  disabled: boolean
  mcSelections: Set<number>
  mcDisplayOrder?: number[]
}>()

const emit = defineEmits<{
  toggleMcOption: [idx: number]
}>()

const mcOptionsRaw = computed<{ text: string; correct?: boolean }[]>(() => {
  const opts = props.config.options
  return Array.isArray(opts) ? (opts as { text: string; correct?: boolean }[]) : []
})

const mcDisplayItems = computed<{ text: string; correct?: boolean; originalIndex: number }[]>(() => {
  const opts = mcOptionsRaw.value
  const order = props.mcDisplayOrder
  if (order && order.length === opts.length) {
    return order.map(origIdx => ({ ...opts[origIdx], originalIndex: origIdx }))
  }
  return opts.map((opt, i) => ({ ...opt, originalIndex: i }))
})
</script>

<template>
  <div class="space-y-2">
    <div
      v-for="item in mcDisplayItems"
      :key="item.originalIndex"
      class="flex items-center gap-2 p-3 rounded-lg border-2 cursor-pointer transition-colors"
      :class="[
        mcSelections.has(item.originalIndex) ? 'border-primary bg-primary/10' : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary/50',
        disabled ? 'pointer-events-none opacity-60' : ''
      ]"
      @click="!disabled && emit('toggleMcOption', item.originalIndex)"
    >
      <font-awesome-icon
        :icon="['fas', mcSelections.has(item.originalIndex) ? 'square-check' : 'square']"
        :class="mcSelections.has(item.originalIndex) ? 'text-primary' : 'text-(--text-muted)'"
      />
      <span class="text-sm flex-1">{{ item.text }}</span>
    </div>
  </div>
</template>
