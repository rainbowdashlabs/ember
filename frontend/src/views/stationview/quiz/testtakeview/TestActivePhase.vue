/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import TestQuestionCard from './TestQuestionCard.vue'
import type { QuizTestAttemptQuestion, QuizQuestion } from '@/api/types'

const props = defineProps<{
  sortedQuestions: QuizTestAttemptQuestion[]
  currentIndex: number
  totalQuestions: number
  answers: Map<number, string>
  timerDisplay: string
  timerExpired: boolean
  isFirstQuestion: boolean
  isLastQuestion: boolean
  questionDetail: QuizQuestion
  config: Record<string, unknown>
  answerParsed: Record<string, unknown>
  connectLeftItems: string[]
  connectRightItems: string[]
  mcDisplayOrder: number[]
}>()

const emit = defineEmits<{
  navigate: [index: number]
  prev: []
  next: []
  submit: []
  setMCAnswer: [optionIndex: number, isMulti: boolean]
  setFillBlankGap: [gapIndex: number, value: string]
  setFreeAnswer: [text: string]
  setConnectPair: [leftIndex: number, rightValue: string]
  setImageTextAnswer: [text: string]
  setTrueFalse: [value: boolean]
  reorderItems: [fromIndex: number, toIndex: number]
  moveOrderItem: [index: number, direction: -1 | 1]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-between">
    <span class="text-sm text-(--text-muted)">
      {{ t('quiz.attempt.questionProgress', { current: props.currentIndex + 1, total: props.totalQuestions }) }}
    </span>
    <span v-if="props.timerDisplay" class="text-sm font-mono font-bold" :class="props.timerExpired ? 'text-error' : 'text-(--text)'">
      <font-awesome-icon :icon="['fas', 'clock']" class="mr-1" />
      {{ props.timerDisplay }}
    </span>
  </div>

  <div class="flex flex-wrap gap-1">
    <IconButton
      v-for="(q, idx) in props.sortedQuestions"
      :key="q.id"
      :icon="['fas', 'circle']"
      :label="String(idx + 1)"
      class="w-7 h-7 rounded-full text-xs font-medium transition-colors"
      :class="{
        'bg-primary text-primary-text': idx === props.currentIndex,
        'bg-primary/20 text-primary': idx !== props.currentIndex && props.answers.has(q.questionId) && props.answers.get(q.questionId) !== '',
        'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)': idx !== props.currentIndex && (!props.answers.has(q.questionId) || props.answers.get(q.questionId) === ''),
      }"
      @click="emit('navigate', idx)"
    >
      {{ idx + 1 }}
    </IconButton>
  </div>

  <TestQuestionCard
    :question-detail="props.questionDetail"
    :config="props.config"
    :answer-parsed="props.answerParsed"
    :connect-left-items="props.connectLeftItems"
    :connect-right-items="props.connectRightItems"
    :mc-display-order="props.mcDisplayOrder"
    @set-m-c-answer="(i, m) => emit('setMCAnswer', i, m)"
    @set-fill-blank-gap="(i, v) => emit('setFillBlankGap', i, v)"
    @set-free-answer="(v) => emit('setFreeAnswer', v)"
    @set-connect-pair="(i, v) => emit('setConnectPair', i, v)"
    @set-image-text-answer="(v) => emit('setImageTextAnswer', v)"
    @set-true-false="(v) => emit('setTrueFalse', v)"
    @reorder-items="(f, t2) => emit('reorderItems', f, t2)"
    @move-order-item="(i, d) => emit('moveOrderItem', i, d)"
  />

  <div class="flex items-center justify-between">
    <SecondaryButton :icon="['fas', 'chevron-left']" :disabled="props.isFirstQuestion" @click="emit('prev')">
      {{ t('quiz.attempt.prev') }}
    </SecondaryButton>

    <SuccessButton :icon="['fas', 'paper-plane']" @click="emit('submit')">
      {{ t('quiz.attempt.submit') }}
    </SuccessButton>

    <SecondaryButton :disabled="props.isLastQuestion" @click="emit('next')">
      {{ t('quiz.attempt.next') }}
      <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-1" />
    </SecondaryButton>
  </div>
</template>
