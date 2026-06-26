/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import type {QuizQuestion, QuizTestAnswer} from '@/api/types'

const props = defineProps<{
  question: QuizQuestion
  answer: QuizTestAnswer | undefined
  isGapCorrect: (answerId: number, gapIndex: number) => boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-gap', answerId: number, gapIndex: number, questionId: number): void
}>()

const {t} = useI18n()

const config = computed<Record<string, unknown>>(() => (props.question.config ?? {}) as Record<string, unknown>)

const promptText = computed<string>(() => (config.value.text as string) ?? '')

const correctAnswers = computed<string[]>(() => (config.value.answers as string[]) ?? [])

const gaps = computed<Record<string, string>>(() => {
  if (!props.answer) return {}
  try {
    const parsed = JSON.parse(props.answer.answer || '{}') as Record<string, unknown>
    return (parsed.gaps as Record<string, string>) ?? {}
  } catch {
    return {}
  }
})

function studentGap(idx: number): string {
  return gaps.value[String(idx)] ?? ''
}
</script>

<template>
  <div v-if="promptText" class="text-sm text-(--text-muted) whitespace-pre-wrap mb-2">
    {{ promptText }}
  </div>
  <div class="space-y-2">
    <div
      v-for="(correctAns, gapIdx) in correctAnswers"
      :key="gapIdx"
      class="flex items-center gap-2 px-3 py-2 rounded border text-sm"
      :class="props.answer && props.isGapCorrect(props.answer.id, gapIdx)
        ? 'border-success/30 bg-success/10'
        : 'border-bg-light-accent dark:border-bg-dark-accent'"
    >
      <span class="text-xs text-(--text-muted) w-5 shrink-0">{{ gapIdx + 1 }}.</span>
      <span class="flex-1">
        {{ studentGap(gapIdx) || '—' }}
      </span>
      <span class="text-xs text-success shrink-0">{{ correctAns }}</span>
      <IconButton
        v-if="props.answer"
        :icon="['fas', props.isGapCorrect(props.answer.id, gapIdx) ? 'check-circle' : 'circle']"
        :label="t('quiz.evaluate.correct')"
        :class="props.isGapCorrect(props.answer.id, gapIdx) ? 'text-success' : 'text-(--text-muted) hover:text-success'"
        @click="emit('toggle-gap', props.answer.id, gapIdx, props.question.id)"
      />
    </div>
  </div>
</template>
