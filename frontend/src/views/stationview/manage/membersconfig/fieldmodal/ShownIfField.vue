/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import LabelledField from '@/components/input/LabelledField.vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {EditableField} from '@/api/profileFields'
import {FieldType} from '@/api/generated/schema'

/**
 * The question this one is only shown behind.
 *
 * <p>Offers the owner's yes/no, choice and text questions except the one being edited, each named with
 * what lets this one in: a yes, or any answer. A question in a group is only offered the questions of
 * that group; a condition it already holds from outside the group stays visible with a warning. A member asked only this question
 * still sees it; the dependency only acts where both are on the same form.
 */
const shownIf = defineModel<number | null>({required: true})

const props = defineProps<{
  conditionFields: EditableField[]
  /** The question being edited, which cannot depend on itself. */
  fieldId: number | null
  /** The group the question is to stand in; inside one it can only depend on a question of that group. */
  setId?: number | null
}>()

const {t} = useI18n()

const inGroup = (field: EditableField) => props.setId == null || field.fieldSetId === props.setId

/**
 * The condition the question holds although it stands outside the question's group, kept in view so
 * the editor sees it and can change it rather than meeting a refusal for something not shown.
 */
const outside = computed(() => props.conditionFields.find(field => field.id === shownIf.value && !inGroup(field)))

const choices = computed(() => props.conditionFields.filter(field => field.id !== props.fieldId && inGroup(field)))

/** The question with what lets the dependent one in, so a choice is not mistaken for a yes/no. */
function optionLabel(field: EditableField): string {
  return field.fieldType === FieldType.BOOLEAN
      ? t('membersConfig.shownIfWhenYes', {name: field.name})
      : t('membersConfig.shownIfWhenAnswered', {name: field.name})
}

const choice = computed({
  get: () => (shownIf.value != null ? String(shownIf.value) : ''),
  set: value => {
    shownIf.value = value ? Number(value) : null
  },
})
</script>

<template>
  <LabelledField v-if="choices.length > 0 || outside" :label="t('membersConfig.shownIf')"
                 :help="t('membersConfig.shownIfHint')" help-as-hint>
    <template #default="{describedBy}">
      <SelectInput v-model="choice" class="w-full" data-testid="field-shown-if" :aria-describedby="describedBy">
        <option value="">{{ t('membersConfig.shownAlways') }}</option>
        <option v-for="field in choices" :key="field.id" :value="String(field.id)">{{ optionLabel(field) }}</option>
        <option v-if="outside" :value="String(outside.id)">
          {{ t('membersConfig.shownIfOutsideGroup', {name: outside.name}) }}
        </option>
      </SelectInput>
      <p v-if="outside" class="text-xs text-warning" data-testid="field-shown-if-outside">
        {{ t('membersConfig.shownIfOutsideGroupHint') }}
      </p>
    </template>
  </LabelledField>
</template>
