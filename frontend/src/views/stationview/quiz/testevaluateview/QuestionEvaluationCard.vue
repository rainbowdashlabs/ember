/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {QuizQuestion, QuizTestAnswer} from '@/api/types'
import QuestionHeader from './QuestionHeader.vue'
import AnswerReviewSwitch from './AnswerReviewSwitch.vue'
import PointsField from './PointsField.vue'

const props = defineProps<{
  question: QuizQuestion
  answer: QuizTestAnswer | undefined
  index: number
  points: number
  canReview: boolean
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
  <NeutralContainer>
    <div class="space-y-3">
      <QuestionHeader
        :question="props.question"
        :answer="props.answer"
        :index="props.index"
        :points="props.points"
      />
      <AnswerReviewSwitch
        :question="props.question"
        :answer="props.answer"
        :is-gap-correct="props.isGapCorrect"
        @toggle-gap="(answerId, gapIndex, questionId) => emit('toggle-gap', answerId, gapIndex, questionId)"
        @mark-correct="(answerId, maxPoints) => emit('mark-correct', answerId, maxPoints)"
        @mark-wrong="(answerId, maxPoints) => emit('mark-wrong', answerId, maxPoints)"
      />
      <PointsField
        :question="props.question"
        :answer="props.answer"
        :points="props.points"
        :can-review="props.canReview"
        @update-points="(answerId, maxPoints, value) => emit('update-points', answerId, maxPoints, value)"
      />
    </div>
  </NeutralContainer>
</template>
