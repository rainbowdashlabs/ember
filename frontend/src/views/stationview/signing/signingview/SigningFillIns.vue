/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type {FillInResponse} from '@/api/generated/schema'
import type {FillInValues} from './fillIns'

/**
 * The fields the document asks the signer to fill in, one input each, labelled as the document labels
 * them.
 *
 * <p>The inputs stand in one group with a legend, so a screen reader announces what they belong to. A
 * required field is marked in its label and as required on the input; the most characters a field takes
 * is held by the input itself. Once the act is started the values are bound into it, so the inputs are
 * locked from then on.
 */
defineProps<{
  fields: FillInResponse[]
  /** Whether the act was started, which binds the values as they stand. */
  locked: boolean
}>()

const values = defineModel<FillInValues>({required: true})

const {t} = useI18n()
const hintId = useId()
const baseId = useId()

function inputId(index: number): string {
  return `${baseId}-${index}`
}

function update(name: string, value: string | undefined) {
  values.value = {...values.value, [name]: value ?? ''}
}
</script>

<template>
  <fieldset class="space-y-3" :aria-describedby="hintId" data-testid="signing-fill-ins">
    <legend class="text-sm font-medium">{{ t('signing.fillIns.legend') }}</legend>
    <MutedText :id="hintId" tag="p" size="sm">{{ t('signing.fillIns.hint') }}</MutedText>
    <div v-for="(field, index) in fields" :key="field.name" class="space-y-1">
      <FieldLabel :for="inputId(index)">
        {{ field.required ? t('signing.fillIns.required', {label: field.label}) : field.label }}
      </FieldLabel>
      <TextInput
          :id="inputId(index)"
          :model-value="values[field.name] ?? ''"
          :disabled="locked"
          :maxlength="field.maxLength"
          :required="field.required"
          :aria-required="field.required"
          :data-testid="`signing-fill-in-${field.name}`"
          @update:model-value="update(field.name, $event)"
      />
    </div>
  </fieldset>
</template>
