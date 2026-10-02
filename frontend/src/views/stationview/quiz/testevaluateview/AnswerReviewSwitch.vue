/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {QuizQuestionType, type QuizQuestion, type QuizTestAnswer} from '@/api/generated/schema'
import McAnswerReview from './McAnswerReview.vue'
import TrueFalseAnswerReview from './TrueFalseAnswerReview.vue'
import ConnectAnswerReview from './ConnectAnswerReview.vue'
import OrderingAnswerReview from './OrderingAnswerReview.vue'
import FillBlankAnswerReview from './FillBlankAnswerReview.vue'
import FreeAnswerReview from './FreeAnswerReview.vue'

const props = defineProps<{
  question: QuizQuestion
  answer: QuizTestAnswer | undefined
  isGapCorrect: (answerId: number, gapIndex: number) => boolean
}>()

const emit = defineEmits<{
  'toggle-gap': [answerId: number, gapIndex: number, questionId: number]
  'mark-correct': [answerId: number, maxPoints: number]
  'mark-wrong': [answerId: number, maxPoints: number]
}>()

const type = computed(() => props.question.quizQuestionType)
</script>

<template>
  <McAnswerReview
    v-if="type === QuizQuestionType.MULTIPLE_CHOICE"
    :question="props.question"
    :answer="props.answer"
  />
  <TrueFalseAnswerReview
    v-else-if="type === QuizQuestionType.TRUE_FALSE"
    :question="props.question"
    :answer="props.answer"
  />
  <ConnectAnswerReview
    v-else-if="type === QuizQuestionType.CONNECT"
    :question="props.question"
    :answer="props.answer"
  />
  <OrderingAnswerReview
    v-else-if="type === QuizQuestionType.ORDERING"
    :question="props.question"
    :answer="props.answer"
  />
  <FillBlankAnswerReview
    v-else-if="type === QuizQuestionType.FILL_IN_THE_BLANK"
    :question="props.question"
    :answer="props.answer"
    :is-gap-correct="props.isGapCorrect"
    @toggle-gap="(answerId, gapIndex, questionId) => emit('toggle-gap', answerId, gapIndex, questionId)"
  />
  <FreeAnswerReview
    v-else
    :question="props.question"
    :answer="props.answer"
    @mark-correct="(answerId, maxPoints) => emit('mark-correct', answerId, maxPoints)"
    @mark-wrong="(answerId, maxPoints) => emit('mark-wrong', answerId, maxPoints)"
  />
</template>
