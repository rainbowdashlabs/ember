/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import type {QuizQuestion, QuizTestAnswer} from '@/api/types'

const props = defineProps<{
  question: QuizQuestion
  answer: QuizTestAnswer | undefined
}>()

const items = computed<string[]>(() => {
  const cfg = (props.question.config ?? {}) as Record<string, unknown>
  return (cfg.items as string[]) ?? []
})

const order = computed<number[]>(() => {
  if (!props.answer) return []
  try {
    const parsed = JSON.parse(props.answer.answer || '{}') as Record<string, unknown>
    return (parsed.order as number[]) ?? []
  } catch {
    return []
  }
})

function labelAt(idx: number): string {
  return items.value[idx] ?? `#${idx}`
}
</script>

<template>
  <div class="space-y-1">
    <div
      v-for="(itemIdx, pos) in order"
      :key="pos"
      class="flex items-center gap-2 px-3 py-1.5 rounded text-xs border"
      :class="itemIdx === pos ? 'border-success/30 bg-success/10' : 'border-error/30 bg-error/10'"
    >
      <span class="text-xs text-(--text-muted) w-5">{{ pos + 1 }}.</span>
      <span>{{ labelAt(itemIdx) }}</span>
      <template v-if="itemIdx !== pos">
        <span class="ml-auto text-xs text-success">
          → {{ labelAt(pos) }}
        </span>
      </template>
    </div>
  </div>
</template>
