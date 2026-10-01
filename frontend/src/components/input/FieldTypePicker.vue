/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from './select/SelectInput.vue'
import {fieldTypeLabel, isFieldType, type FieldTypeName} from '@/api/fieldTypes'

/**
 * The select a field's type is chosen in, whatever feature the field belongs to.
 *
 * <p>Seven screens each wrote this again with their own list and their own words for the same types,
 * so a line of text was "Text" on one and "STRING" spelled out on another. Here the feature only says
 * which types it offers; every type is called the same everywhere.
 *
 * <p>A field already saved under a type the feature no longer offers keeps showing it, so opening an
 * old field does not quietly turn it into the first type on the list.
 *
 * <p>Attributes such as a test id land on the select itself, which is what a test picks from.
 */
defineOptions({inheritAttrs: false})

const type = defineModel<FieldTypeName>({required: true})

const props = defineProps<{
  /** The types this feature offers, in the order it offers them. */
  types: readonly FieldTypeName[]
  /** Types offered but not available just now, such as a second date of birth. */
  unavailable?: readonly FieldTypeName[]
  disabled?: boolean
  /** Whether the line saying what the chosen type is for is shown under the select. */
  describe?: boolean
}>()

const {t, te} = useI18n()

const choices = computed(() => {
  const offered = props.types.includes(type.value) ? props.types : [...props.types, type.value]
  return offered.map(value => ({
    value,
    label: fieldTypeLabel(t, value),
    unavailable: (props.unavailable ?? []).includes(value) && value !== type.value,
  }))
})

const hint = computed(() => {
  const key = `fieldTypes.hint.${type.value}`
  return props.describe && te(key) ? t(key) : ''
})

function choose(value: unknown) {
  if (isFieldType(value)) type.value = value
}
</script>

<template>
  <div class="space-y-1">
    <SelectInput v-bind="$attrs" :disabled="disabled" :model-value="type" class="w-full"
                 @update:model-value="choose">
      <option v-for="choice in choices" :key="choice.value" :disabled="choice.unavailable" :value="choice.value">
        {{ choice.label }}
      </option>
    </SelectInput>
    <p v-if="hint" class="text-xs text-(--text-muted)">{{ hint }}</p>
  </div>
</template>
