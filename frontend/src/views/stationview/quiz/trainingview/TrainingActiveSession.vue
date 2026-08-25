/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TrainingQuestionCard from './TrainingQuestionCard.vue'
import ReportQuestionModal from './ReportQuestionModal.vue'
import type { QuizQuestion } from '@/api/quiz'

const userTfAnswer = defineModel<boolean | null>('userTfAnswer', {required: true})
const userAnswer = defineModel<string>('userAnswer', {required: true})

defineProps<{
  currentQuestion: QuizQuestion
  currentIndex: number
  questionsTotal: number
  progress: string
  progressPercent: number
  showAnswer: boolean
  userMcSelections: Set<number>
  userOrderItems: number[]
  userConnectPairs: Record<string, string>
  userFillGaps: Record<string, string>
  mcDisplayOrder: number[]
  connectRightOrder: number[]
}>()

defineEmits<{
  toggleMcOption: [idx: number]
  reorderItems: [fromIndex: number, toIndex: number]
  moveOrderItem: [index: number, direction: -1 | 1]
  setConnectPair: [leftIndex: number, rightValue: string]
  setFillGap: [gapIndex: number, value: string]
  revealAndNext: []
}>()

const { t } = useI18n()

const showReport = ref(false)
</script>

<template>
  <div class="space-y-1">
    <div class="flex justify-between text-sm text-(--text-muted)">
      <span>{{ t('quiz.training.progress', { current: currentIndex + 1, total: questionsTotal }) }}</span>
      <span>{{ progress }}</span>
    </div>
    <div class="w-full h-2 rounded-full bg-bg-light-accent dark:bg-bg-dark-accent overflow-hidden">
      <div class="h-full rounded-full bg-primary transition-all duration-300" :style="{ width: `${progressPercent}%` }" />
    </div>
  </div>

  <TrainingQuestionCard
    :question="currentQuestion"
    :show-answer="showAnswer"
    v-model:user-answer="userAnswer"
    :user-mc-selections="userMcSelections"
    v-model:user-tf-answer="userTfAnswer"
    :user-order-items="userOrderItems"
    :user-connect-pairs="userConnectPairs"
    :user-fill-gaps="userFillGaps"
    :mc-display-order="mcDisplayOrder"
    :connect-right-order="connectRightOrder"
    @toggle-mc-option="(idx: number) => $emit('toggleMcOption', idx)"
    @reorder-items="(from: number, to: number) => $emit('reorderItems', from, to)"
    @move-order-item="(idx: number, dir: -1 | 1) => $emit('moveOrderItem', idx, dir)"
    @set-connect-pair="(left: number, right: string) => $emit('setConnectPair', left, right)"
    @set-fill-gap="(gap: number, value: string) => $emit('setFillGap', gap, value)"
  />

  <div class="flex justify-between gap-3">
    <SecondaryButton :icon="['fas', 'flag']" @click="showReport = true">
      {{ t('quiz.report.action') }}
    </SecondaryButton>
    <PrimaryButton @click="$emit('revealAndNext')">
      <template v-if="!showAnswer">
        <font-awesome-icon :icon="['fas', 'eye']" class="mr-1" />
        {{ t('quiz.training.showAnswer') }}
      </template>
      <template v-else-if="currentIndex < questionsTotal - 1">
        {{ t('quiz.training.next') }}
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="ml-1" />
      </template>
      <template v-else>
        {{ t('quiz.training.finish') }}
        <font-awesome-icon :icon="['fas', 'check']" class="ml-1" />
      </template>
    </PrimaryButton>
  </div>

  <ReportQuestionModal
      v-model="showReport"
      :question-id="currentQuestion.id"
      :question-title="currentQuestion.title"
  />
</template>
