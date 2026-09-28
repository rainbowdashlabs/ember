/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import QuestionHeading from '@/components/forms/fill/QuestionHeading.vue'
import {QuestionTypes, type ChoiceAnswer, type FormQuestion, type LikertAnswer, type RankingAnswer} from '@/api/forms'
import ChoiceQuestion from './ChoiceQuestion.vue'
import TextQuestion from './TextQuestion.vue'
import RatingQuestion from '@/components/forms/fill/RatingQuestion.vue'
import DateQuestion from './DateQuestion.vue'
import RankingQuestion from '@/components/forms/fill/RankingQuestion.vue'
import LikertQuestion from '@/components/forms/fill/LikertQuestion.vue'

const props = defineProps<{
  question: FormQuestion
  /** What is wrong with the answer, where something is. */
  error?: string
}>()

const answer = defineModel<Record<string, unknown>>({ default: () => ({}) })

function parseConfig(config: Record<string, unknown> | string): Record<string, unknown> {
  if (typeof config === 'object' && config !== null) return config
  try { return JSON.parse(config || '{}') } catch { return {} }
}

const config = computed(() => parseConfig(props.question.config))
</script>

<template>
  <NeutralContainer :class="error ? 'ring-2 ring-error' : ''" :data-question-error="error ? question.id : undefined">
    <div class="space-y-3">
      <QuestionHeading :title="question.title" :description="question.description"
                       :required="question.required" :error="error"/>

      <ChoiceQuestion v-if="question.formQuestionType === QuestionTypes.CHOICE"
                      v-model="(answer as ChoiceAnswer)"
                      :config="config" />
      <TextQuestion v-else-if="question.formQuestionType === QuestionTypes.TEXT"
                    v-model="(answer as { text: string })"
                    :config="config" />
      <RatingQuestion v-else-if="question.formQuestionType === QuestionTypes.RATING"
                      v-model="(answer as { rating: number })"
                      :config="config" />
      <DateQuestion v-else-if="question.formQuestionType === QuestionTypes.DATE"
                    v-model="(answer as { date: string })" />
      <RankingQuestion v-else-if="question.formQuestionType === QuestionTypes.RANKING"
                       v-model="(answer as RankingAnswer)"
                       :config="config" />
      <LikertQuestion v-else-if="question.formQuestionType === QuestionTypes.LIKERT"
                      v-model="(answer as LikertAnswer)"
                      :config="config" />
    </div>
  </NeutralContainer>
</template>
