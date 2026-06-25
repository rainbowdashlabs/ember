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

const pairs = computed<{ left: string; right: string }[]>(() => {
  const cfg = (props.question.config ?? {}) as Record<string, unknown>
  return (cfg.pairs as { left: string; right: string }[]) ?? []
})

const givenPairs = computed<Record<string, string>>(() => {
  if (!props.answer) return {}
  try {
    const parsed = JSON.parse(props.answer.answer || '{}') as Record<string, unknown>
    return (parsed.pairs as Record<string, string>) ?? {}
  } catch {
    return {}
  }
})

function givenFor(index: number): string {
  return givenPairs.value[String(index)] ?? ''
}
</script>

<template>
  <div class="space-y-1">
    <div
      v-for="(pair, pi) in pairs"
      :key="pi"
      class="flex items-center gap-2 px-3 py-1.5 rounded text-xs border"
      :class="givenFor(pi) === pair.right
        ? 'border-success/30 bg-success/10'
        : 'border-error/30 bg-error/10'"
    >
      <span class="font-medium">{{ pair.left }}</span>
      <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
      <span
        :class="givenFor(pi) === pair.right
          ? 'text-success'
          : 'text-error'"
      >
        {{ givenFor(pi) || '—' }}
      </span>
      <template v-if="givenFor(pi) !== pair.right">
        <span class="text-xs text-(--text-muted)">→</span>
        <span class="text-success text-xs">{{ pair.right }}</span>
      </template>
    </div>
  </div>
</template>
