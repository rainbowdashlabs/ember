/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import CsvQuestionCard from './CsvQuestionCard.vue'
import { needsSplit, splitAnswer, type ImportQuestion } from './quizCsvImport'

const props = defineProps<{
  questions: ImportQuestion[]
  status: string
}>()

const { t } = useI18n()

const includedCount = computed(() => props.questions.filter(question => question.included).length)

function toggleInclude(index: number) {
  const question = props.questions[index]
  question.included = !question.included
}

function resplit(index: number) {
  const question = props.questions[index]
  question.splitItems = needsSplit(question.type) ? splitAnswer(question) : []
  question.mcCorrectIndices.clear()
}

function setSeparator(index: number, value: string) {
  props.questions[index].answerSeparator = value
  resplit(index)
}

function toggleMcCorrect(index: number, optionIndex: number) {
  const correct = props.questions[index].mcCorrectIndices
  if (correct.has(optionIndex)) correct.delete(optionIndex)
  else correct.add(optionIndex)
}

function updateSplitItem(index: number, optionIndex: number, value: string) {
  props.questions[index].splitItems[optionIndex] = value
}

function removeSplitItem(index: number, optionIndex: number) {
  const question = props.questions[index]
  question.splitItems.splice(optionIndex, 1)
  const shifted = [...question.mcCorrectIndices]
      .filter(correctIndex => correctIndex !== optionIndex)
      .map(correctIndex => correctIndex > optionIndex ? correctIndex - 1 : correctIndex)
  question.mcCorrectIndices.clear()
  shifted.forEach(correctIndex => question.mcCorrectIndices.add(correctIndex))
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>
        {{ t('quiz.csv.preview') }} ({{ includedCount }} / {{ questions.length }})
      </SectionHeader>
      <span v-if="status" class="text-xs text-(--text-muted)">
        <font-awesome-icon :icon="['fas', 'spinner']" spin class="mr-1" />
        {{ status }}
      </span>
    </div>

    <div class="space-y-3">
      <CsvQuestionCard
          v-for="(question, index) in questions"
          :key="index"
          :question="question"
          :index="index"
          @toggle-include="toggleInclude"
          @resplit="resplit"
          @set-separator="setSeparator"
          @toggle-mc-correct="toggleMcCorrect"
          @update-split-item="updateSplitItem"
          @remove-split-item="removeSplitItem"
      />
    </div>
  </NeutralContainer>
</template>
