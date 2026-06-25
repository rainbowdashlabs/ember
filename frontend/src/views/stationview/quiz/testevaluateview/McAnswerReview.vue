/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type {QuizQuestion, QuizTestAnswer} from '@/api/types'

const props = defineProps<{
  question: QuizQuestion
  answer: QuizTestAnswer | undefined
}>()

const {t} = useI18n()

const options = computed<{ text: string; correct: boolean }[]>(() => {
  const cfg = (props.question.config ?? {}) as Record<string, unknown>
  return (cfg.options as { text: string; correct: boolean }[]) ?? []
})

const selected = computed<number[]>(() => {
  if (!props.answer) return []
  try {
    const parsed = JSON.parse(props.answer.answer || '{}') as Record<string, unknown>
    return (parsed.selected as number[]) ?? []
  } catch {
    return []
  }
})

function isSelected(index: number): boolean {
  return selected.value.includes(index)
}
</script>

<template>
  <div class="space-y-1">
    <div
      v-for="(opt, oi) in options"
      :key="oi"
      class="flex items-center gap-2 px-3 py-1.5 rounded text-xs"
      :class="{
        'bg-success/10 border border-success/30': opt.correct && isSelected(oi),
        'bg-success/5 border border-success/20': opt.correct && !isSelected(oi),
        'bg-error/10 border border-error/30': !opt.correct && isSelected(oi),
        'border border-bg-light-accent dark:border-bg-dark-accent': !opt.correct && !isSelected(oi),
      }"
    >
      <font-awesome-icon
        :icon="['fas', isSelected(oi) ? 'square-check' : 'square']"
        :class="opt.correct ? 'text-success' : isSelected(oi) ? 'text-error' : 'text-(--text-muted)'"
      />
      <span>{{ opt.text }}</span>
      <SuccessBadge v-if="opt.correct" class="ml-auto text-xs">{{ t('quiz.evaluate.correct') }}</SuccessBadge>
    </div>
  </div>
</template>
