/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {QuizQuestion, QuizTestAnswer} from '@/api/types'

const props = defineProps<{
  question: QuizQuestion
  answer: QuizTestAnswer | undefined
}>()

const {t} = useI18n()

const correctAnswer = computed<boolean>(() => {
  const cfg = (props.question.config ?? {}) as Record<string, unknown>
  return (cfg.correctAnswer as boolean) ?? false
})

const studentValue = computed<boolean | undefined>(() => {
  if (!props.answer) return undefined
  try {
    const parsed = JSON.parse(props.answer.answer || '{}') as Record<string, unknown>
    const v = parsed.value
    if (v === true || v === false) return v
    return undefined
  } catch {
    return undefined
  }
})

const isCorrect = computed(() => studentValue.value === correctAnswer.value)

const studentLabel = computed(() => {
  if (studentValue.value === true) return t('quiz.attempt.true')
  if (studentValue.value === false) return t('quiz.attempt.false')
  return t('quiz.noAnswer')
})
</script>

<template>
  <div class="space-y-2 text-sm">
    <div class="flex items-center gap-2">
      <span class="text-(--text-muted)">{{ t('quiz.evaluate.correctAnswer') }}:</span>
      <span class="font-medium">{{ correctAnswer ? t('quiz.attempt.true') : t('quiz.attempt.false') }}</span>
    </div>
    <div class="flex items-center gap-2">
      <span class="text-(--text-muted)">{{ t('quiz.evaluate.studentAnswer') }}:</span>
      <span class="font-medium" :class="isCorrect ? 'text-success' : 'text-error'">
        {{ studentLabel }}
      </span>
      <font-awesome-icon
        :icon="['fas', isCorrect ? 'check' : 'xmark']"
        :class="isCorrect ? 'text-success' : 'text-error'"
      />
    </div>
  </div>
</template>
