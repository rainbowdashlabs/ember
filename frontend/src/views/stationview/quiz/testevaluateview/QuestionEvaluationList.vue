/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {QuizAttemptDetail, QuizQuestion, QuizTestAnswer} from '@/api/types'
import QuestionEvaluationCard from './QuestionEvaluationCard.vue'

const props = defineProps<{
  attempt: QuizAttemptDetail
  questionsMap: Map<number, QuizQuestion>
  canReview: boolean
  getAnswer: (questionId: number) => QuizTestAnswer | undefined
  getPoints: (aq: { questionId: number }) => number
  isGapCorrect: (answerId: number, gapIndex: number) => boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-gap', answerId: number, gapIndex: number, questionId: number): void
  (e: 'mark-correct', answerId: number, maxPoints: number): void
  (e: 'mark-wrong', answerId: number, maxPoints: number): void
  (e: 'update-points', answerId: number, maxPoints: number, value: number | undefined): void
}>()
</script>

<template>
  <div class="space-y-4">
    <template
      v-for="(aq, index) in props.attempt.questions"
      :key="aq.id"
    >
      <QuestionEvaluationCard
        v-if="props.questionsMap.get(aq.questionId)"
        :question="props.questionsMap.get(aq.questionId)!"
        :answer="props.getAnswer(aq.questionId)"
        :index="index"
        :points="props.getPoints(aq)"
        :can-review="props.canReview"
        :is-gap-correct="props.isGapCorrect"
        @toggle-gap="(answerId, gapIndex, questionId) => emit('toggle-gap', answerId, gapIndex, questionId)"
        @mark-correct="(answerId, maxPoints) => emit('mark-correct', answerId, maxPoints)"
        @mark-wrong="(answerId, maxPoints) => emit('mark-wrong', answerId, maxPoints)"
        @update-points="(answerId, maxPoints, value) => emit('update-points', answerId, maxPoints, value)"
      />
    </template>
  </div>
</template>
