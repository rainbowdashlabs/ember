/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type {QuizQuestionTypeName} from '@/api/types'
import FieldLabel from '@/components/typography/FieldLabel.vue'

defineProps<{
  headers: string[]
  questionCol: string
  answerCol: string
  categoryCol: string
  typeCol: string
  pointsCol: string
  answerSeparator: string
  defaultType: QuizQuestionTypeName
  typeOptions: {value: QuizQuestionTypeName; label: string}[]
  splitPresets: {label: string; value: string}[]
}>()

const emit = defineEmits<{
  'update:questionCol': [value: string]
  'update:answerCol': [value: string]
  'update:categoryCol': [value: string]
  'update:typeCol': [value: string]
  'update:pointsCol': [value: string]
  'update:answerSeparator': [value: string]
  'update:defaultType': [value: QuizQuestionTypeName]
  resplitAll: []
}>()

const {t} = useI18n()

function onSepClick(value: string) {
  emit('update:answerSeparator', value)
  emit('resplitAll')
}
</script>

<template>
  <NeutralContainer class="space-y-4 mb-4">
    <SectionHeader>2. {{ t('quiz.csv.columnMapping') }}</SectionHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.csv.questionColumn') }} *</FieldLabel>
        <SelectInput :model-value="questionCol" @update:model-value="emit('update:questionCol', $event ?? questionCol)">
          <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
        </SelectInput>
      </div>
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.csv.answerColumn') }}</FieldLabel>
        <SelectInput :model-value="answerCol" @update:model-value="emit('update:answerCol', $event ?? answerCol)">
          <option value="">&ndash;</option>
          <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
        </SelectInput>
      </div>
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.csv.categoryColumn') }}</FieldLabel>
        <SelectInput :model-value="categoryCol" @update:model-value="emit('update:categoryCol', $event ?? categoryCol)">
          <option value="">&ndash;</option>
          <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
        </SelectInput>
      </div>
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.csv.typeColumn') }}</FieldLabel>
        <SelectInput :model-value="typeCol" @update:model-value="emit('update:typeCol', $event ?? typeCol)">
          <option value="">&ndash;</option>
          <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
        </SelectInput>
      </div>
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.csv.pointsColumn') }}</FieldLabel>
        <SelectInput :model-value="pointsCol" @update:model-value="emit('update:pointsCol', $event ?? pointsCol)">
          <option value="">&ndash;</option>
          <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
        </SelectInput>
      </div>
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.csv.answerSeparator') }}</FieldLabel>
        <div class="flex items-center gap-1">
          <SelectionToggleButton
            v-for="sep in splitPresets"
            :key="sep.value"
            :selected="answerSeparator === sep.value"
            @toggle="onSepClick(sep.value)"
          >
            {{ sep.label }}
          </SelectionToggleButton>
        </div>
      </div>
    </div>
    <div v-if="!typeCol">
      <FieldLabel hint class="mb-1">{{ t('quiz.csv.defaultType') }}</FieldLabel>
      <SelectInput :model-value="defaultType" class="w-64" @update:model-value="emit('update:defaultType', ($event ?? defaultType) as QuizQuestionTypeName)">
        <option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </SelectInput>
    </div>
  </NeutralContainer>
</template>
