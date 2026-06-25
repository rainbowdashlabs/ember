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
import CsvHeaderSelect from './csvcolumnmapping/CsvHeaderSelect.vue'

const questionCol = defineModel<string>('questionCol', {required: true})
const answerCol = defineModel<string>('answerCol', {required: true})
const categoryCol = defineModel<string>('categoryCol', {required: true})
const typeCol = defineModel<string>('typeCol', {required: true})
const pointsCol = defineModel<string>('pointsCol', {required: true})
const answerSeparator = defineModel<string>('answerSeparator', {required: true})
const defaultType = defineModel<QuizQuestionTypeName>('defaultType', {required: true})

defineProps<{
  headers: string[]
  typeOptions: {value: QuizQuestionTypeName; label: string}[]
  splitPresets: {label: string; value: string}[]
}>()

const emit = defineEmits<{
  resplitAll: []
}>()

const {t} = useI18n()

function onSepClick(value: string) {
  answerSeparator.value = value
  emit('resplitAll')
}
</script>

<template>
  <NeutralContainer class="space-y-4 mb-4">
    <SectionHeader>2. {{ t('quiz.csv.columnMapping') }}</SectionHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
      <CsvHeaderSelect
        :label="`${t('quiz.csv.questionColumn')} *`"
        v-model="questionCol"
        :headers="headers"
      />
      <CsvHeaderSelect
        :label="t('quiz.csv.answerColumn')"
        v-model="answerCol"
        :headers="headers"
        optional
      />
      <CsvHeaderSelect
        :label="t('quiz.csv.categoryColumn')"
        v-model="categoryCol"
        :headers="headers"
        optional
      />
      <CsvHeaderSelect
        :label="t('quiz.csv.typeColumn')"
        v-model="typeCol"
        :headers="headers"
        optional
      />
      <CsvHeaderSelect
        :label="t('quiz.csv.pointsColumn')"
        v-model="pointsCol"
        :headers="headers"
        optional
      />
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
      <SelectInput :model-value="defaultType" class="w-64" @update:model-value="(v: string | undefined) => defaultType = (v ?? defaultType) as QuizQuestionTypeName">
        <option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </SelectInput>
    </div>
  </NeutralContainer>
</template>
