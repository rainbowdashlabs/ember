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

const props = defineProps<{
  field: WaitingListField
}>()

const value = defineModel<string>('value', {required: true})

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
      {{ props.field.name }}
      <span v-if="props.field.required" class="text-error">*</span>
    </FieldLabel>

    <TextInput
      v-if="props.field.fieldType === 'TEXT'"
      v-model="value"
    />
    <NumberInput
      v-else-if="props.field.fieldType === 'NUMBER'"
      :model-value="asNumber()"
      @update:model-value="value = String($event ?? 0)"
    />
    <DateInput
      v-else-if="props.field.fieldType === 'DATE' || props.field.fieldType === 'BIRTH_DATE'"
      v-model="value"
    />
    <div v-else-if="props.field.fieldType === 'BOOLEAN'" class="flex items-center gap-2 pt-1">
      <ToggleInput
        :model-value="asBoolean()"
        @update:model-value="value = String($event)"
      />
    </div>
    <SelectInput
      v-else-if="props.field.fieldType === 'ENUM'"
      class="w-full"
      v-model="value"
    >
      <option value="" disabled>{{ t('waitingList.selectOption') }}</option>
      <option
        v-for="opt in (props.field.config?.options ?? [])"
        :key="opt"
        :value="opt"
      >{{ opt }}</option>
    </SelectInput>
  </div>
</template>
