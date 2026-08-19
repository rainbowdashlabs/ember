/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { WaitingListField } from '@/api/waitingList'

const value = defineModel<string>('value', {required: true})

defineProps<{
  field: WaitingListField
}>()

const { t } = useI18n()

function asNumber(): number {
  return value.value ? Number(value.value) : 0
}

function asBoolean(): boolean {
  return value.value === 'true'
}
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>
      {{ field.name }}
      <span v-if="field.required" class="text-error text-xs ml-1">*</span>
    </FieldLabel>
    <TextInput
      v-if="field.fieldType === 'TEXT'"
      v-model="value"
    />
    <NumberInput
      v-else-if="field.fieldType === 'NUMBER'"
      :model-value="asNumber()"
      @update:model-value="value = String($event ?? 0)"
    />
    <DateInput
      v-else-if="field.fieldType === 'DATE'"
      v-model="value"
    />
    <ToggleInput
      v-else-if="field.fieldType === 'BOOLEAN'"
      :model-value="asBoolean()"
      @update:model-value="value = String($event)"
    />
    <SelectInput
      v-else-if="field.fieldType === 'ENUM'"
      v-model="value"
    >
      <option value="" disabled>{{ t('waitingList.selectOption') }}</option>
      <option
        v-for="opt in (field.config?.options ?? [])"
        :key="opt"
        :value="opt"
      >{{ opt }}</option>
    </SelectInput>
  </div>
</template>
