/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MenuSettingRow from './MenuSettingRow.vue'
import type {QuestionDraft} from '../types'

/**
 * The scale of a Likert grid: where it starts and ends, what its steps are called, and whether the
 * statements come in a different order for every reader.
 *
 * <p>A step's label is stored at the position of its value less one, which is where the fill screens
 * read it. A step below one has no such position and is shown by its number alone.
 */
const props = defineProps<{
  question: QuestionDraft
}>()

const {t} = useI18n()

const steps = computed(() => {
  const min = Math.max(1, (props.question.config.scaleMin as number | undefined) ?? 1)
  const max = (props.question.config.scaleMax as number | undefined) ?? 5
  return Array.from({length: Math.max(0, max - min + 1)}, (_, index) => min + index)
})

function labelOf(value: number): string {
  return ((props.question.config.scaleLabels as string[] | undefined) ?? [])[value - 1] ?? ''
}

function setLabel(value: number, label: string | undefined) {
  const labels = [...((props.question.config.scaleLabels as string[] | undefined) ?? [])]
  while (labels.length < value) labels.push('')
  labels[value - 1] = label ?? ''
  props.question.config.scaleLabels = labels
}
</script>

<template>
  <MenuSettingRow :label="t('forms.questionShuffleStatements')">
    <ToggleInput v-model="question.shuffle"/>
  </MenuSettingRow>
  <MenuSettingRow :label="t('forms.likert.scaleMin')">
    <NumberInput v-model="(question.config.scaleMin as number)" class="w-20"/>
  </MenuSettingRow>
  <MenuSettingRow :label="t('forms.likert.scaleMax')">
    <NumberInput v-model="(question.config.scaleMax as number)" class="w-20"/>
  </MenuSettingRow>
  <p class="px-3 pt-1.5 text-xs text-(--text-muted)">{{ t('forms.likert.scaleLabels') }}</p>
  <MenuSettingRow v-for="value in steps" :key="value" :label="String(value)">
    <TextInput :model-value="labelOf(value)" class="w-44" @update:model-value="setLabel(value, $event)"/>
  </MenuSettingRow>
</template>
