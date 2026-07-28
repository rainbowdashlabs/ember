/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import { QuestionTypes } from '@/api/forms'
import type { FormQuestion } from '@/api/forms'
import ChoiceQuestion from './ChoiceQuestion.vue'
import TextQuestion from './TextQuestion.vue'
import RatingQuestion from './RatingQuestion.vue'
import DateQuestion from './DateQuestion.vue'
import RankingQuestion from './RankingQuestion.vue'
import LikertQuestion from './LikertQuestion.vue'

const props = defineProps<{
  question: FormQuestion
}>()

const answer = defineModel<Record<string, unknown>>({ required: true })

function parseConfig(config: Record<string, unknown> | string): Record<string, unknown> {
  if (typeof config === 'object' && config !== null) return config
  try { return JSON.parse(config || '{}') } catch { return {} }
}

const config = computed(() => parseConfig(props.question.config))
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div>
        <span class="font-medium">{{ question.title }}</span>
        <span v-if="question.required" class="text-error ml-1">*</span>
        <MutedText v-if="question.description" tag="p" class="mt-0.5">{{ question.description }}</MutedText>
      </div>

      <ChoiceQuestion v-if="question.formQuestionType === QuestionTypes.CHOICE"
                      v-model="(answer as { selected: number[]; other: string })"
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
                       v-model="(answer as { order: number[] })"
                       :config="config" />
      <LikertQuestion v-else-if="question.formQuestionType === QuestionTypes.LIKERT"
                      v-model="(answer as { ratings: Record<string, number> })"
                      :config="config" />
    </div>
  </NeutralContainer>
</template>
