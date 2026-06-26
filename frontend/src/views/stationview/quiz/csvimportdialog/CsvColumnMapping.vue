/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHint from '@/components/typography/FieldHint.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type { QuizQuestionTypeName } from '@/api/types'
import CsvColumnSelect from '@/views/stationview/quiz/csvimportdialog/CsvColumnSelect.vue'
import CsvDefaultTypeSelect from '@/views/stationview/quiz/csvimportdialog/CsvDefaultTypeSelect.vue'

const questionColumn = defineModel<string>('questionColumn', {required: true})
const answerColumn = defineModel<string>('answerColumn', {required: true})
const categoryColumn = defineModel<string>('categoryColumn', {required: true})
const typeColumn = defineModel<string>('typeColumn', {required: true})
const pointsColumn = defineModel<string>('pointsColumn', {required: true})
const defaultType = defineModel<QuizQuestionTypeName>('defaultType', {required: true})
const answerSeparator = defineModel<string>('answerSeparator', {required: true})

defineProps<{
  headers: string[]
}>()

const { t } = useI18n()

const answerSeparatorModel = computed({
  get: () => answerSeparator.value,
  set: v => { answerSeparator.value = v ?? '' },
})
</script>

<template>
  <div class="space-y-4">
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <CsvColumnSelect
        :label="t('quiz.csv.questionColumn')"
        v-model="questionColumn"
        :headers="headers"
      />
      <CsvColumnSelect
        :label="t('quiz.csv.answerColumn')"
        v-model="answerColumn"
        :headers="headers"
        optional
      />
      <CsvColumnSelect
        :label="t('quiz.csv.categoryColumn')"
        v-model="categoryColumn"
        :headers="headers"
        optional
      />
      <CsvColumnSelect
        :label="t('quiz.csv.typeColumn')"
        v-model="typeColumn"
        :headers="headers"
        optional
      />
      <CsvColumnSelect
        :label="t('quiz.csv.pointsColumn')"
        v-model="pointsColumn"
        :headers="headers"
        optional
      />
      <CsvDefaultTypeSelect
        v-model="defaultType"
      />
    </div>

    <div class="flex items-center gap-2">
      <FieldHint>{{ t('quiz.csv.answerSeparator') }}</FieldHint>
      <TextInput v-model="answerSeparatorModel" class="w-16" />
    </div>
  </div>
</template>
