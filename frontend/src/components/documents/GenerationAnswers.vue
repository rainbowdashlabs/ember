/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {TemplateQuestionKind, type TemplateQuestion} from '@/api/generated/schema'
import type {GenerationAnswers} from '@/api/documentTemplates'

/**
 * What the template asks the person generating one document, prefilled with what each question
 * suggests for the member: a text, a date, or a box to tick. A required question is marked; left empty,
 * it is listed among the missing values of the preview.
 *
 * <p>Answers are kept as the server takes them: a text as written, a date as an ISO date, a tick as
 * `true` or `false`.
 */
const answers = defineModel<GenerationAnswers>({required: true})

defineProps<{
  questions: TemplateQuestion[]
}>()

const {t} = useI18n()

function set(key: string, value: string) {
  answers.value = {...answers.value, [key]: value}
}

function labelOf(question: TemplateQuestion): string {
  return question.required ? `${question.label} *` : question.label
}
</script>

<template>
  <div class="space-y-3" data-testid="generation-answers">
    <SubHeader>{{ t('documentTemplates.answersTitle') }}</SubHeader>
    <template v-for="question in questions" :key="question.key">
      <label v-if="question.kind === TemplateQuestionKind.CHECK" class="flex cursor-pointer items-center gap-2"
             :data-testid="`generation-answer-${question.key}`">
        <CheckboxInput :model-value="answers[question.key] === 'true'"
                       @update:model-value="ticked => set(question.key, String(ticked))"/>
        <span>{{ labelOf(question) }}</span>
      </label>
      <LabelledField v-else :label="labelOf(question)">
        <DateInput v-if="question.kind === TemplateQuestionKind.DATE" :model-value="answers[question.key] ?? ''"
                   :data-testid="`generation-answer-${question.key}`"
                   @update:model-value="value => set(question.key, value ?? '')"/>
        <TextInput v-else :model-value="answers[question.key] ?? ''" :data-testid="`generation-answer-${question.key}`"
                   @update:model-value="value => set(question.key, String(value ?? ''))"/>
      </LabelledField>
    </template>
  </div>
</template>
