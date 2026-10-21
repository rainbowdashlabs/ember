/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import QuestionOptionsEditor from '@/components/input/QuestionOptionsEditor.vue'
import {TemplateQuestionKind, type QuestionLimits} from '@/api/generated/schema'
import DayBoundField from './DayBoundField.vue'
import NumberLimitsEditor from './NumberLimitsEditor.vue'
import TextLimitsEditor from './TextLimitsEditor.vue'

/**
 * What counts as an answer to a question, in the shape its kind has: the days a date takes, the length
 * and format of a text, the bounds of a number, the entries of a choice. A tick has none.
 */
const limits = defineModel<QuestionLimits>({required: true})

defineProps<{
  kind: TemplateQuestionKind
}>()

const {t} = useI18n()
</script>

<template>
  <div v-if="kind === TemplateQuestionKind.DATE" class="grid gap-3 sm:grid-cols-2" data-testid="question-limits">
    <DayBoundField :model-value="limits.earliest" :label="t('documentTemplates.questions.limits.earliest')"
                   @update:model-value="earliest => limits = {...limits, earliest}"/>
    <DayBoundField :model-value="limits.latest" :label="t('documentTemplates.questions.limits.latest')"
                   @update:model-value="latest => limits = {...limits, latest}"/>
  </div>
  <TextLimitsEditor v-else-if="kind === TemplateQuestionKind.TEXT" v-model="limits" data-testid="question-limits"/>
  <NumberLimitsEditor v-else-if="kind === TemplateQuestionKind.NUMBER" v-model="limits" data-testid="question-limits"/>
  <QuestionOptionsEditor v-else-if="kind === TemplateQuestionKind.CHOICE" :model-value="limits.choices"
                         :label="t('documentTemplates.questions.limits.choices')" data-testid="question-limits"
                         @update:model-value="choices => limits = {...limits, choices}"/>
</template>
