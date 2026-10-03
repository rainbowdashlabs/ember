/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {FontFamilyOption} from '@/api/generated/schema'
import {DEFAULT_FONT, choiceValue, familyChoices, familyOf, type FamilyChoice} from './fontOptions'

/**
 * Picks the family of uploaded fonts a text prints in, or the default font, named by the family the
 * instance prints in. Every family the template reaches is offered with who uploaded it; one the
 * template names but no longer reaches is kept and marked, since the document prints it in the default
 * font until it is back or replaced.
 */
const family = defineModel<string | null>({required: true})

const props = defineProps<{
  label: string
  fonts: readonly FontFamilyOption[]
  /** The family a text naming none prints in, Liberation Sans where not given. */
  defaultFamily?: string
  /** Offer only families a field on an uploaded PDF can print in. */
  pdfOnly?: boolean
  testId?: string
}>()

const {t} = useI18n()

const choices = computed(() => familyChoices(props.fonts, family.value, props.pdfOnly))
const defaultFamily = computed(() => props.defaultFamily ?? DEFAULT_FONT)
const selected = computed(() => choiceValue(choices.value, family.value))

function labelOf(choice: FamilyChoice): string {
  if (choice.missing) return t('documentFonts.missingFamily', {family: choice.value, defaultFamily: defaultFamily.value})
  if (!choice.family) return t('documentFonts.defaultFont', {family: defaultFamily.value})
  return t('documentFonts.familyOption', {family: choice.family.family, origin: t(`documentFonts.origin.${choice.family.origin}`)})
}

function choose(value: string | number | null | undefined) {
  family.value = familyOf(String(value ?? ''))
}
</script>

<template>
  <LabelledField :label="label">
    <SelectInput :model-value="selected" class="w-full" :data-testid="testId" @update:model-value="choose">
      <option v-for="choice in choices" :key="choice.value" :value="choice.value">{{ labelOf(choice) }}</option>
    </SelectInput>
  </LabelledField>
</template>
