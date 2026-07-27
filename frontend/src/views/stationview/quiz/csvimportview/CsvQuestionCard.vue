/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import { QuizQuestionTypes } from '@/api/types'
import { ANSWER_SEPARATOR_PRESETS, needsSplit, type ImportQuestion } from './quizCsvImport'

const props = defineProps<{
  question: ImportQuestion
  index: number
}>()

const emit = defineEmits<{
  toggleInclude: [index: number]
  resplit: [index: number]
  setSeparator: [index: number, value: string]
  toggleMcCorrect: [index: number, optionIndex: number]
  updateSplitItem: [index: number, optionIndex: number, value: string]
  removeSplitItem: [index: number, optionIndex: number]
}>()

const { t } = useI18n()

const isMultipleChoice = computed(() => props.question.type === QuizQuestionTypes.MULTIPLE_CHOICE)
const splittable = computed(() => needsSplit(props.question.type))
</script>

<template>
  <NeutralContainer
    class="space-y-3 transition-opacity"
    :class="{ 'opacity-40': !question.included }"
  >
    <div class="flex items-start justify-between gap-2 flex-wrap">
      <div class="flex-1 min-w-0">
        <p class="font-medium text-sm truncate">{{ question.title || t('quiz.csv.noTitle') }}</p>
        <div class="flex items-center gap-2 mt-0.5 flex-wrap">
          <span class="text-xs text-(--text-muted)">{{ t(`quiz.questionTypes.${question.type}`) }}</span>
          <span class="text-xs text-(--text-muted)">&bull;</span>
          <span class="text-xs text-(--text-muted)">{{ question.points }} {{ t('quiz.points') }}</span>
          <span v-if="question.category" class="text-xs text-(--text-muted)">&bull; {{ question.category }}</span>
        </div>
      </div>
      <div class="flex items-center gap-1 shrink-0">
        <IconButton
          :icon="['fas', question.included ? 'eye-slash' : 'eye']"
          :label="question.included ? t('quiz.csv.exclude') : t('quiz.csv.include')"
          @click="emit('toggleInclude', index)"
        />
      </div>
    </div>

    <template v-if="question.included && splittable">
      <div class="flex items-center gap-1 flex-wrap">
        <span class="text-xs text-(--text-muted) mr-1">{{ t('quiz.csv.answerSeparator') }}:</span>
        <SelectionToggleButton
          v-for="preset in ANSWER_SEPARATOR_PRESETS"
          :key="preset.label"
          :selected="question.answerSeparator === preset.value"
          @toggle="emit('setSeparator', index, preset.value)"
        >
          {{ preset.label }}
        </SelectionToggleButton>
        <SecondaryButton class="text-xs" @click="emit('resplit', index)">
          {{ t('quiz.csv.resplit') }}
        </SecondaryButton>
      </div>

      <div v-if="question.splitItems.length > 0" class="space-y-1">
        <div
          v-for="(item, itemIndex) in question.splitItems"
          :key="itemIndex"
          class="flex items-center gap-2"
        >
          <SelectionToggleButton
            v-if="isMultipleChoice"
            :selected="question.mcCorrectIndices.has(itemIndex)"
            @toggle="emit('toggleMcCorrect', index, itemIndex)"
          >
            <font-awesome-icon :icon="['fas', question.mcCorrectIndices.has(itemIndex) ? 'check' : 'xmark']" />
          </SelectionToggleButton>
          <TextInput
            :model-value="item"
            class="flex-1 text-xs"
            @update:model-value="emit('updateSplitItem', index, itemIndex, $event ?? '')"
          />
          <IconButton
            :icon="['fas', 'trash']"
            :label="t('common.delete')"
            class="text-error"
            @click="emit('removeSplitItem', index, itemIndex)"
          />
        </div>
      </div>
      <div v-else class="text-xs text-(--text-muted) italic">
        {{ t('quiz.csv.noSplitItems') }}
      </div>
    </template>

    <p v-else-if="question.included && question.answer" class="text-xs text-(--text-muted)">
      {{ question.answer }}
    </p>
  </NeutralContainer>
</template>
