/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CsvColumnSelect from '@/components/csv/CsvColumnSelect.vue'
import type {CsvMappings, QuizQuestionTypeName} from '@/api/quiz'
import AnswerSeparatorPicker from './AnswerSeparatorPicker.vue'
import {QUIZ_CSV_TYPES} from './quizCsvImport'

const mapping = defineModel<CsvMappings>('mapping', {required: true})

defineProps<{
  headers: string[]
}>()

const {t} = useI18n()

const showFurther = ref(false)

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
      <CsvColumnSelect
          v-model="mapping.distractorColumn"
          :headers="headers"
          :label="t('quiz.csv.distractorColumn')"
          optional
      />
      <AnswerSeparatorPicker v-model:separator="mapping.answerSeparator" />
    </div>

    <p class="text-xs text-(--text-muted)">{{ t('quiz.csv.distractorHint') }}</p>

    <SecondaryButton
        :icon="['fas', showFurther ? 'chevron-up' : 'chevron-down']"
        @click="showFurther = !showFurther"
    >
      {{ showFurther ? t('quiz.csv.hideFurtherColumns') : t('quiz.csv.showFurtherColumns') }}
    </SecondaryButton>

    <div v-if="showFurther" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
      <CsvColumnSelect
          v-model="mapping.descriptionColumn"
          :headers="headers"
          :label="t('quiz.csv.descriptionColumn')"
          optional
      />
      <CsvColumnSelect
          v-model="mapping.imageColumn"
          :headers="headers"
          :label="t('quiz.csv.imageColumn')"
          optional
      />
      <CsvColumnSelect
          v-model="mapping.pointsPerCorrectColumn"
          :headers="headers"
          :label="t('quiz.csv.pointsPerCorrectColumn')"
          optional
      />
      <CsvColumnSelect
          v-model="mapping.requiredCountColumn"
          :headers="headers"
          :label="t('quiz.csv.requiredCountColumn')"
          optional
      />
      <CsvColumnSelect
          v-model="mapping.orderedRequiredColumn"
          :headers="headers"
          :label="t('quiz.csv.orderedRequiredColumn')"
          optional
      />
    </div>

    <div v-if="!mapping.typeColumn">
      <FieldLabel hint class="mb-1">{{ t('quiz.csv.defaultType') }}</FieldLabel>
      <SelectInput :model-value="mapping.defaultType" class="w-64" @update:model-value="onDefaultTypeUpdate">
        <option v-for="type in QUIZ_CSV_TYPES" :key="type" :value="type">{{ t(`quiz.questionTypes.${type}`) }}</option>
      </SelectInput>
    </div>
  </NeutralContainer>
</template>
