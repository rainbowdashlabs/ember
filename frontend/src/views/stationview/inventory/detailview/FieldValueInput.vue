/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldAnswerInput from '@/components/input/FieldAnswerInput.vue'
import {numberFieldViolation} from '@/api/inventoryFields'
import {FieldTypes} from '@/api/fieldTypes'
import type {EnumConfig, InventoryFieldDefinition, NumberConfig, TextConfig} from '@/api/generated/schema'

/**
 * Answering one of the fields a piece of equipment carries.
 *
 * <p>The box is the one every question is answered in. What stays here is this feature's own: its
 * values are typed rather than text, which is the shape the rest of Ember is being brought to, so
 * this turns them into text on the way in and back into a number, a date or a yes on the way out.
 */
const props = defineProps<{
  field: InventoryFieldDefinition
}>()

const value = defineModel<unknown>({required: true})

const {t} = useI18n()

const numberError = computed(() => {
  const violation = numberFieldViolation(props.field, value.value)
  if (!violation) return ''
  return violation.limit === 'min'
      ? t('inventory.fields.numberBelowMin', {min: violation.bound})
      : t('inventory.fields.numberAboveMax', {max: violation.bound})
})

const textConfig = computed<TextConfig | null>(() => (props.field.config.kind === 'TEXT' ? props.field.config : null))
const numberConfig = computed<NumberConfig | null>(() => (props.field.config.kind === 'NUMBER' ? props.field.config : null))
const enumConfig = computed<EnumConfig | null>(() => (props.field.config.kind === 'CHOICE' ? props.field.config : null))

/** A long answer is a text field the station marked as one, which no other feature says separately. */
const answerType = computed(() => (textConfig.value?.multiline ? FieldTypes.LONG_TEXT : props.field.fieldType))

const asText = computed(() => (value.value == null ? '' : String(value.value)))

/** Back into the shape a piece of equipment stores: a number, a yes or a no, or nothing at all. */
function write(next: string) {
  if (next === '') {
    value.value = props.field.fieldType === FieldTypes.BOOLEAN ? false : null
    return
  }
  if (props.field.fieldType === FieldTypes.NUMBER) {
    const parsed = Number(next)
    value.value = Number.isNaN(parsed) ? null : parsed
    return
  }
  if (props.field.fieldType === FieldTypes.BOOLEAN) {
    value.value = next === 'true'
    return
  }
  value.value = next
}
</script>

<template>
  <FieldAnswerInput
      :field-type="answerType"
      :max="numberConfig?.max"
      :max-length="textConfig?.maxLength || undefined"
      :min="numberConfig?.min"
      :model-value="asText"
      :options="enumConfig?.options ?? []"
      :step="numberConfig?.step"
      @update:model-value="write($event)"
  />
  <p v-if="numberError" class="text-xs text-error mt-1">{{ numberError }}</p>
</template>
