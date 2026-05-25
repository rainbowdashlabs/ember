/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type { QuizQuestionTypeName } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'

export interface ImportQuestion {
  title: string
  answer: string
  category: string
  type: QuizQuestionTypeName
  points: number
  included: boolean
  answerSepOverride: string
  rawRow: string[]
  mcCorrectIndices: Set<number>
  mcPointsPerCorrect: number
  enumRequiredCount: number
  enumOrderRequired: boolean
  splitItems: string[]
}

const props = defineProps<{
  question: ImportQuestion
  index: number
  headers: string[]
  answerSeparator: string
  typeOptions: { value: QuizQuestionTypeName; label: string }[]
  splitPresets: { label: string; value: string }[]
}>()

const emit = defineEmits<{
  remove: [index: number]
  restore: [index: number]
  resplit: [index: number]
  setSeparatorOverride: [index: number, value: string]
  toggleMcCorrect: [index: number, optionIndex: number]
  updateSplitItem: [index: number, optionIndex: number, newValue: string]
  removeSplitItem: [index: number, optionIndex: number]
}>()

const { t } = useI18n()

function onSepClick(value: string) {
  emit('setSeparatorOverride', props.index, value)
  emit('resplit', props.index)
}

const isMC = () => props.question.type === QuizQuestionTypes.MULTIPLE_CHOICE
const needsSplit = () =>
  props.question.type === QuizQuestionTypes.MULTIPLE_CHOICE ||
  props.question.type === QuizQuestionTypes.ORDERING ||
  props.question.type === QuizQuestionTypes.FILL_IN_THE_BLANK ||
  props.question.type === QuizQuestionTypes.ENUMERATION
</script>

<template>
  <NeutralContainer
    class="space-y-3 transition-opacity"
    :class="{ 'opacity-40': !question.included }"
  >
    <!-- Header row -->
    <div class="flex items-start justify-between gap-2 flex-wrap">
      <div class="flex-1 min-w-0">
        <p class="font-medium text-sm truncate">{{ question.title || t('quiz.csv.noTitle') }}</p>
        <div class="flex items-center gap-2 mt-0.5 flex-wrap">
          <span class="text-xs text-(--text-muted)">{{ question.type }}</span>
          <span class="text-xs text-(--text-muted)">&bull;</span>
          <span class="text-xs text-(--text-muted)">{{ question.points }} {{ t('quiz.points') }}</span>
          <span v-if="question.category" class="text-xs text-(--text-muted)">&bull; {{ question.category }}</span>
        </div>
      </div>
      <div class="flex items-center gap-1 shrink-0">
        <IconButton
          v-if="question.included"
          :icon="['fas', 'eye-slash']"
          :label="t('quiz.csv.exclude')"
          @click="emit('remove', index)"
        />
        <IconButton
          v-else
          :icon="['fas', 'eye']"
          :label="t('quiz.csv.include')"
          @click="emit('restore', index)"
        />
      </div>
    </div>

    <!-- Split items for types that need it -->
    <template v-if="question.included && needsSplit()">
      <!-- Separator override buttons -->
      <div class="flex items-center gap-1 flex-wrap">
        <span class="text-xs text-(--text-muted) mr-1">{{ t('quiz.csv.answerSeparator') }}:</span>
        <SelectionToggleButton
          v-for="sep in splitPresets"
          :key="sep.value"
          :selected="(question.answerSepOverride || answerSeparator) === sep.value"
          @toggle="onSepClick(sep.value)"
        >
          {{ sep.label }}
        </SelectionToggleButton>
        <SecondaryButton class="text-xs !py-0.5 !px-2" @click="emit('resplit', index)">
          {{ t('quiz.csv.resplit') }}
        </SecondaryButton>
      </div>

      <!-- Split items list -->
      <div v-if="question.splitItems.length > 0" class="space-y-1">
        <div
          v-for="(item, i) in question.splitItems"
          :key="i"
          class="flex items-center gap-2"
        >
          <SelectionToggleButton
            v-if="isMC()"
            :selected="question.mcCorrectIndices.has(i)"
            @toggle="emit('toggleMcCorrect', index, i)"
          >
            <font-awesome-icon :icon="['fas', question.mcCorrectIndices.has(i) ? 'check' : 'xmark']" />
          </SelectionToggleButton>
          <input
            :value="item"
            class="flex-1 text-xs px-2 py-1 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary"
            @input="emit('updateSplitItem', index, i, ($event.target as HTMLInputElement).value)"
          />
          <IconButton
            :icon="['fas', 'trash']"
            :label="t('common.delete')"
            class="text-error"
            @click="emit('removeSplitItem', index, i)"
          />
        </div>
      </div>
      <div v-else class="text-xs text-(--text-muted) italic">
        {{ t('quiz.csv.noSplitItems') }}
      </div>
    </template>

    <!-- Answer preview for non-split types -->
    <template v-else-if="question.included && question.answer">
      <p class="text-xs text-(--text-muted)">{{ question.answer }}</p>
    </template>
  </NeutralContainer>
</template>
