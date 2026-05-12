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

const props = defineProps<{
  fieldType: string
  modelValue: string
  options?: string[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function getBool(): boolean {
  return props.modelValue === 'true'
}

function setBool(val: boolean) {
  emit('update:modelValue', String(val))
}
</script>

<template>
  <template v-if="fieldType === 'boolean'">
    <ToggleInput :disabled="disabled" :model-value="getBool()" @update:model-value="setBool($event)"/>
  </template>
  <template v-else-if="fieldType === 'date'">
    <DateInput :disabled="disabled" :model-value="modelValue"
               @update:model-value="emit('update:modelValue', $event ?? '')"/>
  </template>
  <template v-else-if="fieldType === 'number'">
    <NumberInput :disabled="disabled" :model-value="Number(modelValue) || 0"
                 @update:model-value="emit('update:modelValue', String($event ?? 0))"/>
  </template>
  <template v-else-if="fieldType === 'enum'">
    <SelectInput :disabled="disabled" :model-value="modelValue"
                 @update:model-value="emit('update:modelValue', $event ?? '')">
      <option value="">—</option>
      <option v-for="opt in options ?? []" :key="opt" :value="opt">{{ opt }}</option>
    </SelectInput>
  </template>
  <template v-else>
    <TextInput :disabled="disabled" :model-value="modelValue"
               @update:model-value="emit('update:modelValue', $event ?? '')"/>
  </template>
</template>
