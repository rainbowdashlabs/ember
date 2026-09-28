/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import MenuSettingRow from './MenuSettingRow.vue'
import type {QuestionDraft} from '../types'

/**
 * How a choice question is answered: one option or several and how many, as a dropdown, with a free
 * "other" field, and whether its options come in a different order for every reader.
 *
 * <p>A dropdown takes one answer, so turning it on turns several answers off and the other way round.
 */
const props = defineProps<{
  question: QuestionDraft
}>()

const {t} = useI18n()

function setMultiSelect(value: boolean) {
  props.question.config.multiSelect = value
  if (value) props.question.config.dropdown = false
}

function setDropdown(value: boolean) {
  props.question.config.dropdown = value
  if (!value) return
  props.question.config.multiSelect = false
  props.question.config.multiLimitType = 'NONE'
  props.question.config.multiLimit = null
}
</script>

<template>
  <MenuSettingRow :label="t('forms.questionShuffle')">
    <ToggleInput v-model="question.shuffle"/>
  </MenuSettingRow>
  <MenuSettingRow :label="t('forms.choice.multiSelect')">
    <ToggleInput :model-value="!!question.config.multiSelect" @update:model-value="setMultiSelect"/>
  </MenuSettingRow>
  <template v-if="question.config.multiSelect">
    <MenuSettingRow :label="t('forms.choice.limit')">
      <SelectInput v-model="(question.config.multiLimitType as string)" class="w-32">
        <option value="NONE">{{ t('forms.choice.limitNone') }}</option>
        <option value="EXACTLY">{{ t('forms.choice.limitEqual') }}</option>
        <option value="AT_MOST">{{ t('forms.choice.limitAtMost') }}</option>
        <option value="AT_LEAST">{{ t('forms.choice.limitAtLeast') }}</option>
      </SelectInput>
    </MenuSettingRow>
    <MenuSettingRow v-if="question.config.multiLimitType && question.config.multiLimitType !== 'NONE'"
                    :label="t('forms.choice.limitValue')">
      <NumberInput v-model="(question.config.multiLimit as number)" class="w-20"/>
    </MenuSettingRow>
  </template>
  <MenuSettingRow :label="t('forms.choice.dropdown')">
    <ToggleInput :model-value="!!question.config.dropdown" @update:model-value="setDropdown"/>
  </MenuSettingRow>
  <MenuSettingRow :label="t('forms.choice.allowOther')">
    <ToggleInput v-model="(question.config.allowOther as boolean)"/>
  </MenuSettingRow>
</template>
