/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {FormQuestionType} from '@/api/generated/schema'
import MenuSettingRow from './MenuSettingRow.vue'
import ChoiceSettings from './ChoiceSettings.vue'
import RatingSettings from './RatingSettings.vue'
import LikertSettings from './LikertSettings.vue'
import type {QuestionDraft} from '../types'

/**
 * The settings of one question that have a sensible default and only refine it, for the top of its
 * menu. A date question has none, and then nothing is drawn, the divider under it included.
 */
const question = defineModel<QuestionDraft>('question', {required: true})

const {t} = useI18n()

const hasSettings = computed(() => question.value.questionType !== FormQuestionType.DATE)
</script>

<template>
  <template v-if="hasSettings">
    <ChoiceSettings v-if="question.questionType === FormQuestionType.CHOICE" v-model:question="question"/>
    <MenuSettingRow v-else-if="question.questionType === FormQuestionType.TEXT" :label="t('forms.text.longAnswer')">
      <ToggleInput v-model="(question.config.longAnswer as boolean)"/>
    </MenuSettingRow>
    <RatingSettings v-else-if="question.questionType === FormQuestionType.RATING" :question="question"/>
    <MenuSettingRow v-else-if="question.questionType === FormQuestionType.RANKING" :label="t('forms.questionShuffle')">
      <ToggleInput v-model="question.shuffle"/>
    </MenuSettingRow>
    <LikertSettings v-else-if="question.questionType === FormQuestionType.LIKERT" v-model:question="question"/>
    <hr class="my-1 border-(--border)"/>
  </template>
</template>
