/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import CsvQuestionCard from './CsvQuestionCard.vue'
import {answerList, resplitAnswers, setAnswerList, toggleCorrect, type ImportDraft} from './quizCsvImport'
import type {QuizCatalogExportCategory} from '@/api/quiz'

const props = defineProps<{
  drafts: ImportDraft[]
  categories: QuizCatalogExportCategory[]
  status: string
}>()

const {t} = useI18n()

const includedCount = computed(() => props.drafts.filter(draft => draft.included).length)
const categoryNames = computed(() => new Map(props.categories.map(category => [category.key, category.name])))

function categoryName(draft: ImportDraft): string {
  const key = draft.question.categoryKey
  return key ? categoryNames.value.get(key) ?? key : ''
}

function at(index: number): ImportDraft | undefined {
  return props.drafts[index]
}

function toggleInclude(index: number) {
  const draft = at(index)
  if (draft) draft.included = !draft.included
}

function resplit(index: number) {
  const draft = at(index)
  if (draft) resplitAnswers(draft)
}

function setSeparator(index: number, value: string) {
  const draft = at(index)
  if (!draft) return
  draft.answerSeparator = value
  resplitAnswers(draft)
}

function onToggleCorrect(index: number, answerIndex: number) {
  const draft = at(index)
  if (draft) toggleCorrect(draft, answerIndex)
}

function updateAnswer(index: number, answerIndex: number, value: string) {
  const draft = at(index)
  if (!draft) return
  const answers = [...answerList(draft)]
  answers[answerIndex] = value
  setAnswerList(draft, answers)
}

function removeAnswer(index: number, answerIndex: number) {
  const draft = at(index)
  if (!draft) return
  const answers = [...answerList(draft)]
  answers.splice(answerIndex, 1)
  setAnswerList(draft, answers)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>
        {{ t('quiz.csv.preview') }} ({{ includedCount }} / {{ drafts.length }})
      </SectionHeader>
      <span v-if="status" class="text-xs text-(--text-muted)">
        <font-awesome-icon :icon="['fas', 'spinner']" spin class="mr-1" />
        {{ status }}
      </span>
    </div>

    <div class="space-y-3">
      <CsvQuestionCard
          v-for="(draft, index) in drafts"
          :key="index"
          :draft="draft"
          :index="index"
          :category-name="categoryName(draft)"
          @toggle-include="toggleInclude"
          @resplit="resplit"
          @set-separator="setSeparator"
          @toggle-correct="onToggleCorrect"
          @update-answer="updateAnswer"
          @remove-answer="removeAnswer"
      />
    </div>
  </NeutralContainer>
</template>
