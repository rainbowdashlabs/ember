/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import PublicChoiceQuestion from './PublicChoiceQuestion.vue'
import RatingQuestion from './RatingQuestion.vue'
import RankingQuestion from './RankingQuestion.vue'
import LikertQuestion from './LikertQuestion.vue'
import {FormQuestionType, type FormAnswerValue, type PublicFormQuestion} from '@/api/generated/schema'

/**
 * The fields one question of a public form is answered with.
 *
 * <p>One component for both places a stranger meets a form: the page it was sent to them on, and the
 * form embedded in a public page. They drew their own fields each, and between them they drew three
 * of the six kinds of question, so a poll asking for a rating showed a heading over empty space and
 * then refused the answer.
 *
 * <p>Rating, ranking and Likert write into the answer they are handed rather than replacing it,
 * which is what the station's own fill screen has always relied on. That is why they take the answer
 * and give nothing back, while text, date and choice report what was typed or picked.
 *
 * <p>Every answer names the kind of question it answers, so the field drawn is the one its answer
 * is shaped for.
 */
const props = defineProps<{
  question: PublicFormQuestion
  answer: FormAnswerValue | undefined
}>()

const emit = defineEmits<{
  'update:text': [text: string]
  'update:date': [date: string]
  'toggle-choice': [optionKey: string]
}>()

const longAnswer = computed(() => props.question.config.questionType === FormQuestionType.TEXT && !!props.question.config.longAnswer)
</script>

<template>
    <template v-if="answer?.type === FormQuestionType.TEXT">
        <TextAreaInput v-if="longAnswer"
                       :model-value="answer.text"
                       @update:model-value="(v?: string) => emit('update:text', v ?? '')"/>
        <TextInput v-else
                   :model-value="answer.text"
                   @update:model-value="(v?: string) => emit('update:text', v ?? '')"/>
    </template>
    <template v-else-if="answer?.type === FormQuestionType.DATE">
        <DateInput :model-value="answer.date"
                   @update:model-value="(v?: string) => emit('update:date', v ?? '')"/>
    </template>
    <template v-else-if="answer?.type === FormQuestionType.CHOICE">
        <PublicChoiceQuestion
            :question="question"
            :answer="answer"
            @toggle="(key: string) => emit('toggle-choice', key)"/>
    </template>
    <template v-else-if="answer?.type === FormQuestionType.RATING">
        <RatingQuestion :config="question.config" :model-value="answer"/>
    </template>
    <template v-else-if="answer?.type === FormQuestionType.RANKING">
        <RankingQuestion :config="question.config" :model-value="answer"/>
    </template>
    <template v-else-if="answer?.type === FormQuestionType.LIKERT">
        <LikertQuestion :config="question.config" :model-value="answer"/>
    </template>
</template>
