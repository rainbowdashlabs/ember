/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {ResultFieldCondition} from '@/api/forms'
import {FieldTypes, type ProfileField} from '@/api/profileFields'

/**
 * Conditions on profile answers: the answers that count for a choice or yes/no field, a range for a
 * number field. Each field can be conditioned once; a condition is added by choosing its field.
 */
const props = defineProps<{
  fields: ProfileField[]
}>()

const conditions = defineModel<ResultFieldCondition[]>({required: true})

const {t} = useI18n()

const adding = ref<string | number | null>(null)

const unused = computed(() => props.fields.filter(field => !conditions.value.some(c => c.fieldId === field.id)))

function fieldOf(condition: ResultFieldCondition): ProfileField | undefined {
  return props.fields.find(field => field.id === condition.fieldId)
}

function answerOptions(field: ProfileField | undefined) {
  if (field?.fieldType === FieldTypes.BOOLEAN) {
    return [
      {value: 'true', label: t('forms.analytics.grouping.yes')},
      {value: 'false', label: t('forms.analytics.grouping.no')},
    ]
  }
  return ((field?.config?.options as string[] | undefined) ?? []).map(option => ({value: option, label: option}))
}

function add(fieldId: string | number | null | undefined) {
  if (fieldId === null || fieldId === undefined || fieldId === '') return
  conditions.value = [...conditions.value, {fieldId: Number(fieldId), values: [], from: null, to: null}]
  adding.value = null
}

function update(index: number, part: Partial<ResultFieldCondition>) {
  conditions.value = conditions.value.map((condition, i) => i === index ? {...condition, ...part} : condition)
}

function remove(index: number) {
  conditions.value = conditions.value.filter((_, i) => i !== index)
}

function bound(value: number | undefined): number | null {
  return Number.isFinite(value) ? value! : null
}
</script>

<template>
  <div class="space-y-2">
    <div v-for="(condition, index) in conditions" :key="condition.fieldId" class="flex items-end gap-2">
      <div class="flex-1 space-y-1">
        <FieldLabel>{{ fieldOf(condition)?.name }}</FieldLabel>
        <div v-if="fieldOf(condition)?.fieldType === FieldTypes.NUMBER" class="flex items-center gap-2">
          <NumberInput :model-value="condition.from ?? undefined" :placeholder="t('forms.analytics.grouping.from')"
                       @update:model-value="value => update(index, {from: bound(value)})"/>
          <NumberInput :model-value="condition.to ?? undefined" :placeholder="t('forms.analytics.grouping.to')"
                       @update:model-value="value => update(index, {to: bound(value)})"/>
        </div>
        <MultiSelectDropdown v-else :model-value="condition.values ?? []" :options="answerOptions(fieldOf(condition))"
                             :placeholder="t('forms.analytics.grouping.choose')"
                             @update:model-value="values => update(index, {values})"/>
      </div>
      <MutedIconButton :icon="['fas', 'xmark']" :label="t('forms.analytics.grouping.removeField')" hover="error"
                       @click="remove(index)"/>
    </div>
    <div v-if="unused.length" class="max-w-xs">
      <SelectInput v-model="adding" @update:model-value="add">
        <option :value="null" disabled>{{ t('forms.analytics.grouping.addField') }}</option>
        <option v-for="field in unused" :key="field.id" :value="field.id">{{ field.name }}</option>
      </SelectInput>
    </div>
  </div>
</template>
