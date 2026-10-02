/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import QuestionHeading from '@/components/forms/fill/QuestionHeading.vue'
import {QuestionTypes} from '@/api/forms'
import type {FormAnswerValue, FormQuestion} from '@/api/generated/schema'
import ChoiceQuestion from './ChoiceQuestion.vue'
import TextQuestion from './TextQuestion.vue'
import RatingQuestion from '@/components/forms/fill/RatingQuestion.vue'
import DateQuestion from './DateQuestion.vue'
import RankingQuestion from '@/components/forms/fill/RankingQuestion.vue'
import LikertQuestion from '@/components/forms/fill/LikertQuestion.vue'

defineProps<{
  question: FormQuestion
  /** What is wrong with the answer, where something is. */
  error?: string
}>()

/**
 * The answer, which names the kind of question it answers and so decides which field is drawn. Every
 * question opens with one; none draws only the heading.
 */
const answer = defineModel<FormAnswerValue>()
</script>

<template>
  <NeutralContainer :class="error ? 'ring-2 ring-error' : ''" :data-question-error="error ? question.id : undefined">
    <div class="space-y-3">
      <QuestionHeading :title="question.title" :description="question.description"
                       :required="question.required" :error="error"/>

      <ChoiceQuestion v-if="answer?.type === QuestionTypes.CHOICE"
                      v-model="answer"
                      :config="question.config" />
      <TextQuestion v-else-if="answer?.type === QuestionTypes.TEXT"
                    v-model="answer"
                    :config="question.config" />
      <RatingQuestion v-else-if="answer?.type === QuestionTypes.RATING"
                      v-model="answer"
                      :config="question.config" />
      <DateQuestion v-else-if="answer?.type === QuestionTypes.DATE"
                    v-model="answer" />
      <RankingQuestion v-else-if="answer?.type === QuestionTypes.RANKING"
                       v-model="answer"
                       :config="question.config" />
      <LikertQuestion v-else-if="answer?.type === QuestionTypes.LIKERT"
                      v-model="answer"
                      :config="question.config" />
    </div>
  </NeutralContainer>
</template>
