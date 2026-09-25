/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import PublicChoiceQuestion from './PublicChoiceQuestion.vue'
import RatingQuestion from './RatingQuestion.vue'
import RankingQuestion from './RankingQuestion.vue'
import LikertQuestion from './LikertQuestion.vue'
import type {PublicFormQuestion} from '@/api/publicForms'
import {QuestionTypes} from '@/api/forms'

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
 */
interface TextAnswer { text: string }
interface DateAnswer { date: string }
interface ChoiceAnswer { selected: number[]; other: string }
interface RatingAnswer { rating: number }
interface RankingAnswer { order: number[] }
interface LikertAnswer { ratings: Record<string, number> }

/**
 * Answers travel as plain records, because which shape one has follows from the question type beside
 * it and no single type can say that. Each branch below reads the shape its own question guarantees.
 */
const props = defineProps<{
  question: PublicFormQuestion
  answer: Record<string, unknown> | undefined
}>()

const emit = defineEmits<{
  (e: 'update:text', text: string): void
  (e: 'update:date', date: string): void
  (e: 'toggle-choice', optionIndex: number): void
}>()

/**
 * The answer read as the shape its own question type guarantees. One place that narrows, so the
 * template says which kind of question it is drawing and nothing else.
 */
function shaped<T>(): T {
  return (props.answer ?? {}) as T
}

function textValue(): string {
  return shaped<Partial<TextAnswer>>().text ?? ''
}

function dateValue(): string {
  return shaped<Partial<DateAnswer>>().date ?? ''
}
</script>

<template>
    <template v-if="question.questionType === QuestionTypes.TEXT">
        <TextAreaInput v-if="question.config.longAnswer"
                       :model-value="textValue()"
                       @update:model-value="(v?: string) => emit('update:text', v ?? '')"/>
        <TextInput v-else
                   :model-value="textValue()"
                   @update:model-value="(v?: string) => emit('update:text', v ?? '')"/>
    </template>

    <template v-else-if="question.questionType === QuestionTypes.DATE">
        <DateInput :model-value="dateValue()"
                   @update:model-value="(v?: string) => emit('update:date', v ?? '')"/>
    </template>

    <template v-else-if="question.questionType === QuestionTypes.CHOICE">
        <PublicChoiceQuestion
            :question="question"
            :answer="shaped<ChoiceAnswer>()"
            @toggle="(oi: number) => emit('toggle-choice', oi)"/>
    </template>

    <template v-else-if="question.questionType === QuestionTypes.RATING">
        <RatingQuestion :config="question.config" :model-value="shaped<RatingAnswer>()"/>
    </template>

    <template v-else-if="question.questionType === QuestionTypes.RANKING">
        <RankingQuestion :config="question.config" :model-value="shaped<RankingAnswer>()"/>
    </template>

    <template v-else-if="question.questionType === QuestionTypes.LIKERT">
        <LikertQuestion :config="question.config" :model-value="shaped<LikertAnswer>()"/>
    </template>
</template>
