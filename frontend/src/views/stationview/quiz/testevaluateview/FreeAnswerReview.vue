/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import IconButton from '@/components/button/IconButton.vue'
import {QuizQuestionTypes} from '@/api/quiz'
import type {QuizQuestion, QuizTestAnswer} from '@/api/quiz'

const props = defineProps<{
  question: QuizQuestion
  answer: QuizTestAnswer | undefined
}>()

const emit = defineEmits<{
  (e: 'mark-correct', answerId: number, maxPoints: number): void
  (e: 'mark-wrong', answerId: number, maxPoints: number): void
}>()

const {t} = useI18n()

const studentText = computed<string>(() => {
  if (!props.answer) return ''
  try {
    const parsed = JSON.parse(props.answer.answer || '{}') as Record<string, unknown>
    return (parsed.text as string) ?? ''
  } catch {
    return ''
  }
})

const sampleAnswers = computed<string>(() => {
  const cfg = (props.question.config ?? {}) as Record<string, unknown>
  const list = (cfg.answers as string[]) ?? []
  return list.join(', ')
})

const showSampleAnswers = computed(() =>
  props.question.quizQuestionType === QuizQuestionTypes.FREE_ANSWER
    || props.question.quizQuestionType === QuizQuestionTypes.ENUMERATION,
)
</script>

<template>
  <div class="space-y-2">
    <div>
      <FieldLabel hint class="mb-1">{{ t('quiz.evaluate.studentAnswer') }}</FieldLabel>
      <p class="text-sm px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent whitespace-pre-wrap">
        {{ studentText || t('quiz.noAnswer') }}
      </p>
    </div>
    <div v-if="showSampleAnswers">
      <label class="text-xs font-semibold text-success block mb-1">{{ t('quiz.evaluate.sampleAnswers') }}</label>
      <p class="text-sm px-3 py-2 rounded border border-success/30 bg-success/10">
        {{ sampleAnswers || '—' }}
      </p>
    </div>
    <div v-if="props.answer" class="flex items-center gap-2">
      <IconButton
        :icon="['fas', 'check']"
        :label="t('quiz.evaluate.markCorrect')"
        class="text-success hover:bg-success/10 rounded px-2 py-1"
        @click="emit('mark-correct', props.answer.id, props.question.points)"
      />
      <IconButton
        :icon="['fas', 'xmark']"
        :label="t('quiz.evaluate.markWrong')"
        class="text-error hover:bg-error/10 rounded px-2 py-1"
        @click="emit('mark-wrong', props.answer.id, props.question.points)"
      />
    </div>
  </div>
</template>
