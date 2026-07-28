/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import { QuestionTypes } from '@/api/forms'
import type { QuestionDraft } from './types'
import QuestionHeader from './QuestionHeader.vue'
import ChoiceConfigEditor from './ChoiceConfigEditor.vue'
import RatingConfigEditor from './RatingConfigEditor.vue'
import LikertConfigEditor from './LikertConfigEditor.vue'
import OptionList from './OptionList.vue'

const props = defineProps<{
  question: QuestionDraft
  index: number
  totalQuestions: number
}>()

const emit = defineEmits<{
  move: [index: number, direction: -1 | 1]
  remove: [index: number]
}>()

const { t } = useI18n()

const q = props.question

function updateRankingOptions(items: string[]) {
  q.config.options = items
}
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <QuestionHeader :question-type="q.questionType" :index="index" :total-questions="totalQuestions"
                      @move="(i, d) => emit('move', i, d)" @remove="(i) => emit('remove', i)" />

      <TextInput v-model="q.title" :placeholder="t('forms.questionTitle')" />
      <TextInput v-model="q.description" :placeholder="t('forms.questionDescription')" />

      <div class="flex gap-4">
        <FieldLabel inline>
          <ToggleInput v-model="q.required" />
          {{ t('forms.questionRequired') }}
        </FieldLabel>
        <label v-if="q.questionType === QuestionTypes.CHOICE || q.questionType === QuestionTypes.RANKING || q.questionType === QuestionTypes.LIKERT"
               class="flex items-center gap-2 text-xs">
          <ToggleInput v-model="q.shuffle" />
          {{ t('forms.questionShuffle') }}
        </label>
      </div>

      <ChoiceConfigEditor v-if="q.questionType === QuestionTypes.CHOICE" :question="q" />

      <FieldLabel v-if="q.questionType === QuestionTypes.TEXT" inline>
        <ToggleInput v-model="(q.config.longAnswer as boolean)" />
        {{ t('forms.text.longAnswer') }}
      </FieldLabel>

      <RatingConfigEditor v-if="q.questionType === QuestionTypes.RATING" :question="q" />

      <OptionList v-if="q.questionType === QuestionTypes.RANKING" :items="(q.config.options as string[])"
                  :label="t('forms.ranking.options')" :add-label="t('forms.ranking.addOption')"
                  @update:items="updateRankingOptions" />

      <LikertConfigEditor v-if="q.questionType === QuestionTypes.LIKERT" :question="q" />
    </div>
  </NeutralContainer>
</template>
