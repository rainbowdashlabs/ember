/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import QuestionInputCard from '../questioncard/QuestionInputCard.vue'
import type { QuizQuestion } from '@/api/quiz'
import { QuizQuestionTypes } from '@/api/quiz'

const userTfAnswer = defineModel<boolean | null>('userTfAnswer', {required: true})
const userAnswer = defineModel<string>('userAnswer', {required: true})

const props = defineProps<{
  question: QuizQuestion
  showAnswer: boolean
  userMcSelections: Set<number>
  userOrderItems: number[]
  userConnectPairs: Record<string, string>
  userFillGaps: Record<string, string>
  mcDisplayOrder?: number[]
  connectRightOrder?: number[]
  /** See {@code QuestionInputCard.directImageSrc}. */
  directImageSrc?: string | null
}>()

const emit = defineEmits<{
  toggleMcOption: [idx: number]
  reorderItems: [fromIndex: number, toIndex: number]
  moveOrderItem: [index: number, direction: -1 | 1]
  setConnectPair: [leftIndex: number, rightValue: string]
  setFillGap: [gapIndex: number, value: string]
}>()

const { t } = useI18n()

const config = computed<Record<string, unknown>>(() => props.question.config ?? {})

const mcOptions = computed<{ text: string; correct?: boolean }[]>(() => {
  const opts = config.value.options
  return Array.isArray(opts) ? (opts as { text: string; correct?: boolean }[]) : []
})

const correctTf = computed<boolean | undefined>(() => config.value.correctAnswer as boolean | undefined)

const correctOrderItems = computed<string[]>(() => (config.value.items as string[]) ?? [])

const correctPairs = computed<{ left: string; right: string }[]>(() => {
  return (config.value.pairs as { left: string; right: string }[]) ?? []
})
</script>

<template>
  <QuestionInputCard
    :question="question"
    :config="config"
    :disabled="showAnswer"
    :mc-selections="userMcSelections"
    v-model:tf-answer="userTfAnswer"
    v-model:free-answer="userAnswer"
    :fill-gaps="userFillGaps"
    :order-items="userOrderItems"
    :connect-pairs="userConnectPairs"
    :mc-display-order="mcDisplayOrder"
    :connect-right-order="connectRightOrder"
    :direct-image-src="directImageSrc"
    @toggle-mc-option="(i: number) => emit('toggleMcOption', i)"
    @set-fill-gap="(gi: number, v: string) => emit('setFillGap', gi, v)"
    @reorder-items="(from: number, to: number) => emit('reorderItems', from, to)"
    @move-order-item="(i: number, d: -1 | 1) => emit('moveOrderItem', i, d)"
    @set-connect-pair="(li: number, rv: string) => emit('setConnectPair', li, rv)"
  >
    <!-- MC answer reveal -->
    <template v-if="showAnswer && question.quizQuestionType === QuizQuestionTypes.MULTIPLE_CHOICE">
      <div class="space-y-1 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
        <span class="text-xs text-(--text-muted)">{{ t('quiz.training.correctAnswer') }}:</span>
        <div v-for="(opt, i) in mcOptions" :key="i" class="flex items-center gap-2 text-sm">
          <font-awesome-icon
            v-if="opt.correct"
            :icon="['fas', 'check']"
            class="text-success"
          />
          <span v-if="opt.correct" class="text-success font-medium">{{ opt.text }}</span>
        </div>
      </div>
    </template>

    <!-- TF answer reveal -->
    <div v-if="showAnswer && question.quizQuestionType === QuizQuestionTypes.TRUE_FALSE" class="text-sm">
      <span class="text-(--text-muted)">{{ t('quiz.training.correctAnswer') }}: </span>
      <span :class="correctTf ? 'text-success' : 'text-error'">{{ correctTf ? t('quiz.attempt.true') : t('quiz.attempt.false') }}</span>
    </div>

    <!-- Free answer reveal -->
    <template v-if="showAnswer && question.quizQuestionType === QuizQuestionTypes.FREE_ANSWER">
      <div v-if="(config.answers as string[] | undefined)?.length" class="space-y-1">
        <span class="text-xs text-(--text-muted)">{{ t('quiz.training.sampleAnswer') }}:</span>
        <p v-for="(ans, i) in (config.answers as string[])" :key="i" class="text-sm text-success">{{ ans }}</p>
      </div>
      <SuccessContainer class="text-xs">
        {{ t('quiz.training.checkYourAnswer') }}
      </SuccessContainer>
    </template>

    <!-- Image Text answer reveal -->
    <div v-if="showAnswer && question.quizQuestionType === QuizQuestionTypes.IMAGE_TEXT && (config.answer as string)" class="space-y-1">
      <span class="text-xs text-(--text-muted)">{{ t('quiz.training.correctAnswer') }}:</span>
      <p class="text-sm text-success">{{ config.answer as string }}</p>
    </div>

    <!-- Fill in the blank answer reveal -->
    <template v-if="showAnswer && question.quizQuestionType === QuizQuestionTypes.FILL_IN_THE_BLANK">
      <div class="space-y-1">
        <span class="text-xs text-(--text-muted)">{{ t('quiz.training.correctAnswer') }}:</span>
        <div v-for="(ans, i) in ((config.answers as string[]) ?? [])" :key="i" class="flex items-center gap-2 text-sm text-success">
          <span class="text-xs text-(--text-muted) w-5 text-right">{{ i + 1 }}.</span>
          <span class="font-medium">{{ ans }}</span>
        </div>
      </div>
    </template>

    <!-- Ordering answer reveal -->
    <div v-if="showAnswer && question.quizQuestionType === QuizQuestionTypes.ORDERING" class="space-y-1">
      <span class="text-xs text-(--text-muted)">{{ t('quiz.training.correctOrder') }}:</span>
      <div v-for="(item, i) in correctOrderItems" :key="i" class="flex items-center gap-2 text-xs text-success">
        <span class="w-5 text-right">{{ i + 1 }}.</span>
        <span>{{ item }}</span>
      </div>
    </div>

    <!-- Connect answer reveal -->
    <div v-if="showAnswer && question.quizQuestionType === QuizQuestionTypes.CONNECT" class="space-y-1">
      <span class="text-xs text-(--text-muted)">{{ t('quiz.training.correctPairs') }}:</span>
      <div v-for="pair in correctPairs" :key="pair.left" class="text-xs text-success flex items-center gap-2">
        <span>{{ pair.left }}</span>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-xs" />
        <span>{{ pair.right }}</span>
      </div>
    </div>

    <!-- Enumeration answer reveal -->
    <div v-if="showAnswer && question.quizQuestionType === QuizQuestionTypes.ENUMERATION && (config.answers as string[] | undefined)?.length" class="space-y-1">
      <span class="text-xs text-(--text-muted)">{{ t('quiz.training.correctAnswer') }}:</span>
      <div v-for="(ans, i) in (config.answers as string[])" :key="i" class="flex items-center gap-1 text-sm text-success">
        <span class="text-xs text-(--text-muted) w-5 text-right">{{ i + 1 }}.</span>
        <span>{{ ans }}</span>
      </div>
    </div>
  </QuestionInputCard>
</template>
