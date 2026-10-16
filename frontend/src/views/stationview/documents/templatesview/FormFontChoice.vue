/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FontFamilyPicker from '@/components/documents/fonts/FontFamilyPicker.vue'
import type {FontFamilyOption, MissingFonts} from '@/api/generated/schema'
import {provideFontSamples} from '@/composables/useFontSamples'
import type {FontSampleAddress} from '@/api/documentFonts'

/**
 * Chooses the font each font of a Word form is drawn in that the station or association has not got,
 * before the form is converted: one of the fonts it reaches, each shown with a line of sample text, or
 * the default font. Nothing is replaced without a choice: a font a built-in one stands in for starts on
 * that one, marked where it sets every letter as wide (Carlito for Calibri) rather than only looking alike
 * (Open Sans for Frutiger); every other font starts on the default font, which is named.
 */
const props = defineProps<{
  missing: MissingFonts
  fonts: readonly FontFamilyOption[]
  sample: FontSampleAddress
}>()

const emit = defineEmits<{
  cancel: []
  choose: [replacements: Record<string, string>]
}>()

const {t} = useI18n()
provideFontSamples(props.sample)

const chosen = ref<Record<string, string | null>>(
    Object.fromEntries(props.missing.missing.map(family => [family, props.missing.suggested[family]?.family ?? null])))

/** What a picker is called: the font it replaces, and where one is offered of the same measure, that it is. */
function labelOf(family: string): string {
  const suggestion = props.missing.suggested[family]
  return suggestion?.sameMeasure
    ? t('documentTemplates.formImport.fontForSameMeasure', {family, replacement: suggestion.family})
    : t('documentTemplates.formImport.fontFor', {family})
}

function confirm() {
  emit('choose', Object.fromEntries(Object.entries(chosen.value).map(([family, replacement]) => [family, replacement ?? ''])))
}
</script>

<template>
  <Modal :model-value="true" size="lg" @update:model-value="emit('cancel')">
    <div class="space-y-4" data-testid="form-font-choice">
      <SubHeader>{{ t('documentTemplates.formImport.fontsTitle') }}</SubHeader>
      <MutedText tag="p">{{ t('documentTemplates.formImport.fontsHint') }}</MutedText>
      <FontFamilyPicker v-for="family in missing.missing" :key="family" :model-value="chosen[family] ?? null"
                        :label="labelOf(family)" :fonts="fonts"
                        :default-family="missing.defaultFamily" :test-id="`form-font-${family}`"
                        @update:model-value="replacement => chosen[family] = replacement"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'file-import']" data-testid="form-font-confirm" @click="confirm">
          {{ t('documentTemplates.formImport.convert') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
