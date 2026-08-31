/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {useI18n} from 'vue-i18n'

const {t} = useI18n()

const modelValue = defineModel<string>({required: true})

defineProps<{
  fieldType: string
  options?: string[]
  disabled?: boolean
  /**
   * Whether an answer is expected. A selection that must be answered still offers its empty entry,
   * because a question never answered has to show as unanswered rather than as its first option,
   * but that entry cannot be chosen: picking nothing on purpose is what this stops.
   */
  required?: boolean
}>()

function getBool(): boolean {
  return modelValue.value === 'true'
}

function setBool(val: boolean) {
  modelValue.value = String(val)
}
</script>

<template>
  <template v-if="fieldType === 'BOOLEAN'">
    <ToggleInput :disabled="disabled" :model-value="getBool()" @update:model-value="setBool($event)"/>
  </template>
  <template v-else-if="fieldType === 'DATE' || fieldType === 'BIRTH_DATE'">
    <DateInput :disabled="disabled" :model-value="modelValue"
               @update:model-value="modelValue = $event ?? ''"/>
  </template>
  <template v-else-if="fieldType === 'NUMBER' || fieldType === 'AGE'">
    <NumberInput :disabled="disabled" :model-value="Number(modelValue) || 0"
                 @update:model-value="modelValue = String($event ?? 0)"/>
  </template>
  <template v-else-if="fieldType === 'ENUM'">
    <SelectInput :disabled="disabled" :model-value="modelValue"
                 @update:model-value="modelValue = String($event ?? '')">
      <option value="" :disabled="required">{{ required ? t('profile.chooseOne') : '-' }}</option>
      <option v-for="opt in options ?? []" :key="opt" :value="opt">{{ opt }}</option>
    </SelectInput>
  </template>
  <template v-else>
    <TextInput :disabled="disabled" :model-value="modelValue"
               @update:model-value="modelValue = $event ?? ''"/>
  </template>
</template>
