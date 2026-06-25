/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import OptionList from './OptionList.vue'
import type { QuestionDraft } from './types'

const props = defineProps<{
  question: QuestionDraft
}>()

const { t } = useI18n()

const q = props.question

function setMultiSelect(val: boolean) {
  q.config.multiSelect = val
  if (val) q.config.dropdown = false
}

function setDropdown(val: boolean) {
  q.config.dropdown = val
  if (val) {
    q.config.multiSelect = false
    q.config.multiLimitType = 'NONE'
    q.config.multiLimit = null
  }
}

function updateOptions(items: string[]) {
  q.config.options = items
}
</script>

<template>
  <div class="flex gap-4 flex-wrap">
    <FieldLabel inline>
      <ToggleInput :model-value="!!q.config.multiSelect" @update:model-value="setMultiSelect($event)" />
      {{ t('forms.choice.multiSelect') }}
    </FieldLabel>
    <FieldLabel inline>
      <ToggleInput :model-value="!!q.config.dropdown" @update:model-value="setDropdown($event)" />
      {{ t('forms.choice.dropdown') }}
    </FieldLabel>
    <FieldLabel inline>
      <ToggleInput v-model="(q.config.allowOther as boolean)" />
      {{ t('forms.choice.allowOther') }}
    </FieldLabel>
  </div>
  <div v-if="q.config.multiSelect" class="flex gap-4 items-center">
    <SelectInput v-model="(q.config.multiLimitType as string)" class="w-40">
      <option value="NONE">{{ t('forms.choice.limitNone') }}</option>
      <option value="EQUAL_TO">{{ t('forms.choice.limitEqual') }}</option>
      <option value="AT_MOST">{{ t('forms.choice.limitAtMost') }}</option>
      <option value="AT_LEAST">{{ t('forms.choice.limitAtLeast') }}</option>
    </SelectInput>
    <NumberInput v-if="q.config.multiLimitType !== 'NONE'" v-model="(q.config.multiLimit as number)"
                 :placeholder="t('forms.choice.limitValue')" class="w-24" />
  </div>
  <OptionList :items="(q.config.options as string[])" :label="t('forms.choice.options')"
              :add-label="t('forms.choice.addOption')" @update:items="updateOptions" />
</template>
