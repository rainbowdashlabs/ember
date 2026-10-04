/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import DropdownPanel from '@/components/input/select/dropdown/DropdownPanel.vue'
import DropdownTrigger from '@/components/input/select/dropdown/DropdownTrigger.vue'
import type {FontFamilyOption} from '@/api/generated/schema'
import FontChoiceList from './FontChoiceList.vue'
import {choiceValue, familyChoices, familyOf, fontEntries} from './fontOptions'

/**
 * Picks the family a text prints in, or the default font, named by the family the instance prints in.
 * Every family the template reaches is offered with where it comes from and a line of sample text the
 * server draws in it; one the template names but no longer reaches is kept and marked, since the
 * document prints it in the default font until it is back or replaced.
 */
const family = defineModel<string | null>({required: true})

const props = defineProps<{
  label: string
  fonts: readonly FontFamilyOption[]
  /** The family a text naming none prints in, as the owner's font list names it. */
  defaultFamily: string
  /** Offer only families a field on an uploaded PDF can print in. */
  pdfOnly?: boolean
  testId?: string
}>()

const {t} = useI18n()

const open = ref(false)
const choices = computed(() => familyChoices(props.fonts, family.value, props.pdfOnly))
const entries = computed(() => fontEntries(choices.value, {family: props.defaultFamily}))
const selected = computed(() => choiceValue(choices.value, family.value))

/** The current choice as the closed picker reads it, origin and all, the way the list marks it. */
const current = computed(() => {
  const entry = entries.value.find(candidate => candidate.value === selected.value)
  if (!entry) return ''
  if (entry.missing) return t('documentFonts.missingFamily', {family: entry.name, defaultFamily: props.defaultFamily})
  if (entry.origin === 'DEFAULT') return t('documentFonts.defaultFont', {family: entry.name})
  const origin = entry.origin ? t(`documentFonts.origin.${entry.origin}`) : ''
  return t('documentFonts.familyOption', {family: entry.name, origin})
})

function choose(value: string) {
  family.value = familyOf(value)
  open.value = false
}
</script>

<template>
  <LabelledField :label="label">
    <DropdownPanel v-model:open="open" match-width panel-class="max-h-80">
      <template #trigger>
        <DropdownTrigger :open="open" :aria-label="t('documentFonts.pickerLabel', {label, family: current})"
                         :data-testid="testId">
          {{ current }}
        </DropdownTrigger>
      </template>
      <FontChoiceList :model-value="selected" :entries="entries" :label="label"
                      :missing-note="t('documentFonts.printedIn', {family: defaultFamily})"
                      @update:model-value="choose"/>
    </DropdownPanel>
  </LabelledField>
</template>
