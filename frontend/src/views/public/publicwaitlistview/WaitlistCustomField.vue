/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {WaitingListField} from '@/api/types'

const props = defineProps<{
  field: WaitingListField
  value: string
}>()

const emit = defineEmits<{
  (e: 'update', value: string): void
}>()

const {t} = useI18n()

const enumOptions = computed<string[]>(() => {
  if (!props.field.config) return []
  try {
    const parsed = JSON.parse(props.field.config) as {options?: string[]}
    return parsed.options ?? []
  } catch {
    return []
  }
})
</script>

<template>
  <div class="space-y-1">
    <FormLabel>{{ field.name }}{{ field.required ? ' *' : '' }}</FormLabel>
    <TextInput v-if="field.fieldType === 'TEXT'" :model-value="value" @update:model-value="emit('update', $event)"/>
    <NumberInput v-else-if="field.fieldType === 'NUMBER'" :model-value="Number(value) || 0" @update:model-value="emit('update', String($event))"/>
    <DateInput v-else-if="field.fieldType === 'DATE'" :model-value="value" @update:model-value="emit('update', $event)"/>
    <ToggleInput v-else-if="field.fieldType === 'BOOLEAN'" :model-value="value === 'true'" @update:model-value="emit('update', String($event))"/>
    <SelectInput v-else-if="field.fieldType === 'ENUM'" :model-value="value" @update:model-value="emit('update', $event)">
      <option value="">{{ t('waitingList.selectOption') }}</option>
      <option v-for="opt in enumOptions" :key="opt" :value="opt">{{ opt }}</option>
    </SelectInput>
  </div>
</template>
