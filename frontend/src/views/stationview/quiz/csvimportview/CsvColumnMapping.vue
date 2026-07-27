/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import CsvColumnSelect from '@/components/csv/CsvColumnSelect.vue'
import type {QuizQuestionTypeName} from '@/api/types'
import {ANSWER_SEPARATOR_PRESETS, QUIZ_CSV_TYPES, type QuizCsvMapping} from './quizCsvImport'

const mapping = defineModel<QuizCsvMapping>('mapping', {required: true})

defineProps<{
  headers: string[]
}>()

const {t} = useI18n()

function onDefaultTypeUpdate(value: string | number | null | undefined) {
  if (value === null || value === undefined) return
  mapping.value.defaultType = String(value) as QuizQuestionTypeName
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('quiz.csv.columnMapping') }}</SubHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
      <CsvColumnSelect
          v-model="mapping.questionColumn"
          :headers="headers"
          :label="`${t('quiz.csv.questionColumn')} *`"
      />
      <CsvColumnSelect
          v-model="mapping.answerColumn"
          :headers="headers"
          :label="t('quiz.csv.answerColumn')"
          optional
      />
      <CsvColumnSelect
          v-model="mapping.categoryColumn"
          :headers="headers"
          :label="t('quiz.csv.categoryColumn')"
          optional
      />
      <CsvColumnSelect
          v-model="mapping.typeColumn"
          :headers="headers"
          :label="t('quiz.csv.typeColumn')"
          optional
      />
      <CsvColumnSelect
          v-model="mapping.pointsColumn"
          :headers="headers"
          :label="t('quiz.csv.pointsColumn')"
          optional
      />
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.csv.answerSeparator') }}</FieldLabel>
        <div class="flex items-center gap-1">
          <SelectionToggleButton
              v-for="preset in ANSWER_SEPARATOR_PRESETS"
              :key="preset.label"
              :selected="mapping.answerSeparator === preset.value"
              @toggle="mapping.answerSeparator = preset.value"
          >
            {{ preset.label }}
          </SelectionToggleButton>
        </div>
      </div>
    </div>

    <div v-if="!mapping.typeColumn">
      <FieldLabel hint class="mb-1">{{ t('quiz.csv.defaultType') }}</FieldLabel>
      <SelectInput :model-value="mapping.defaultType" class="w-64" @update:model-value="onDefaultTypeUpdate">
        <option v-for="type in QUIZ_CSV_TYPES" :key="type" :value="type">{{ t(`quiz.questionTypes.${type}`) }}</option>
      </SelectInput>
    </div>
  </NeutralContainer>
</template>
