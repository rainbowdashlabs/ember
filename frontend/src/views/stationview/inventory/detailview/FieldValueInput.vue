/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {FieldType, numberFieldViolation, type EnumFieldConfig, type InventoryFieldDefinition, type NumberFieldConfig, type TextFieldConfig} from '@/api/inventoryFields'

const props = defineProps<{
  field: InventoryFieldDefinition
}>()

const value = defineModel<any>({required: true})

const {t} = useI18n()

const numberError = computed(() => {
  const violation = numberFieldViolation(props.field, value.value)
  if (!violation) return ''
  return violation.limit === 'min'
      ? t('inventory.fields.numberBelowMin', {min: violation.bound})
      : t('inventory.fields.numberAboveMax', {max: violation.bound})
})

const textConfig = computed(() => props.field.config as TextFieldConfig)
const numberConfig = computed(() => props.field.config as NumberFieldConfig)
const enumConfig = computed(() => props.field.config as EnumFieldConfig)
</script>

<template>
  <template v-if="field.fieldType === FieldType.TEXT">
    <TextAreaInput v-if="textConfig.multiline" v-model="value" :maxlength="textConfig.maxLength || undefined" :rows="3" />
    <TextInput v-else v-model="value" :maxlength="textConfig.maxLength || undefined" />
  </template>
  <template v-else-if="field.fieldType === FieldType.NUMBER">
    <DecimalInput v-if="(numberConfig.step ?? 1) < 1" v-model="value" :min="numberConfig.min ?? undefined" :max="numberConfig.max ?? undefined" />
    <NumberInput v-else v-model="value" :min="numberConfig.min ?? undefined" :max="numberConfig.max ?? undefined" />
    <p v-if="numberError" class="text-xs text-error mt-1">{{ numberError }}</p>
  </template>
  <template v-else-if="field.fieldType === FieldType.DATE">
    <DateInput v-model="value" />
  </template>
  <template v-else-if="field.fieldType === FieldType.ENUM">
    <SelectInput v-model="value">
      <option :value="null">—</option>
      <option v-for="opt in enumConfig.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </SelectInput>
  </template>
  <template v-else-if="field.fieldType === FieldType.BOOLEAN">
    <ToggleInput v-model="value" />
  </template>
</template>
