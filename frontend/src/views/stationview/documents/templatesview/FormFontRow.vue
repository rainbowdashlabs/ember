/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FontFamilyPicker from '@/components/documents/fonts/FontFamilyPicker.vue'
import type {FontFamilyOption} from '@/api/generated/schema'
import {FORM_FONT_SIZE, type FormFontChoice, formFontSizeTaken, type FormFontWeight} from '@/api/documentTemplates'

/**
 * How one font of a Word form is drawn instead: the family, the weight, and the size in percent of the
 * form's, so a replacement that sets its letters wider can be drawn a little smaller and keep the lines of
 * the form. Light is offered only for a family that ships a light cut; choosing a family without one goes
 * back to regular. A size outside what a form takes says so under the field rather than being corrected
 * while it is typed.
 */
const choice = defineModel<FormFontChoice>({required: true})

const props = defineProps<{
  label: string
  fonts: readonly FontFamilyOption[]
  defaultFamily: string
  /** The families that ship a light cut. */
  lightCuts: readonly string[]
  testId: string
}>()

const {t} = useI18n()
const WEIGHTS: readonly FormFontWeight[] = ['LIGHT', 'REGULAR']

const sizeError = computed(() => formFontSizeTaken(choice.value.size)
  ? undefined
  : t('documentTemplates.formImport.sizeRange', {min: FORM_FONT_SIZE.min, max: FORM_FONT_SIZE.max}))

const lightOffered = computed(() => offersLight(choice.value.family))

function offersLight(family: string): boolean {
  return props.lightCuts.includes(family || props.defaultFamily)
}

function setFamily(family: string | null) {
  const next = family ?? ''
  choice.value = {...choice.value, family: next, weight: offersLight(next) ? choice.value.weight : 'REGULAR'}
}

function setSize(size: number | undefined) {
  choice.value = {...choice.value, size: size ?? Number.NaN}
}

function setWeight(weight: string | number | null | undefined) {
  choice.value = {...choice.value, weight: weight === 'LIGHT' ? 'LIGHT' : 'REGULAR'}
}
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-[1fr_9rem_9rem] sm:items-start">
    <FontFamilyPicker :model-value="choice.family || null" :label="label" :fonts="fonts" :default-family="defaultFamily"
                      :test-id="testId" @update:model-value="setFamily"/>
    <LabelledField :label="t('documentTemplates.formImport.weight')">
      <SelectInput :model-value="choice.weight" :disabled="!lightOffered" class="w-full"
                   :data-testid="`${testId}-weight`" @update:model-value="setWeight">
        <option v-for="weight in WEIGHTS" :key="weight" :value="weight">
          {{ t(`documentTemplates.formImport.weights.${weight}`) }}
        </option>
      </SelectInput>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.formImport.size')" :error="sizeError">
      <NumberInput :model-value="choice.size" :data-testid="`${testId}-size`" @update:model-value="setSize"/>
    </LabelledField>
  </div>
</template>
