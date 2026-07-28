/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PublicFormQuestionChoiceField from './PublicFormQuestionChoiceField.vue'
import type {PublicFormQuestion} from '@/api/publicForms'
import {QuestionTypes} from '@/api/forms'

interface TextAnswer { text: string }
interface DateAnswer { date: string }
interface ChoiceAnswer { selected: number[]; other: string }

type AnswerValue = TextAnswer | DateAnswer | ChoiceAnswer | Record<string, unknown>

const props = defineProps<{
  question: PublicFormQuestion
  answer: AnswerValue
}>()

const emit = defineEmits<{
  (e: 'update:text', text: string): void
  (e: 'update:date', date: string): void
  (e: 'toggle-choice', optionIndex: number): void
}>()

function textValue(): string {
  return (props.answer as TextAnswer).text ?? ''
}

function dateValue(): string {
  return (props.answer as DateAnswer).date ?? ''
}

function choiceAnswer(): ChoiceAnswer {
  return props.answer as ChoiceAnswer
}
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div>
        <span class="font-medium">{{ question.title }}</span>
        <span v-if="question.required" class="ml-1 text-error">*</span>
        <MutedText v-if="question.description" tag="p" class="mt-0.5">{{ question.description }}</MutedText>
      </div>

      <template v-if="question.questionType === QuestionTypes.TEXT">
        <TextAreaInput v-if="question.config.longAnswer"
                       :model-value="textValue()"
                       @update:model-value="(v: string) => emit('update:text', v)"/>
        <TextInput v-else
                   :model-value="textValue()"
                   @update:model-value="(v: string) => emit('update:text', v)"/>
      </template>

      <template v-if="question.questionType === QuestionTypes.DATE">
        <DateInput :model-value="dateValue()"
                   @update:model-value="(v: string) => emit('update:date', v)"/>
      </template>

      <template v-if="question.questionType === QuestionTypes.CHOICE">
        <PublicFormQuestionChoiceField
            :question="question"
            :answer="choiceAnswer()"
            @toggle="(oi: number) => emit('toggle-choice', oi)"/>
      </template>
    </div>
  </NeutralContainer>
</template>
