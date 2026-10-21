/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {FontFamilyOption, MissingFonts} from '@/api/generated/schema'
import {provideFontSamples} from '@/composables/useFontSamples'
import type {FontSampleAddress} from '@/api/documentFonts'
import {
  FORM_FONT_SIZE,
  type FormFontChoice,
  type FormFontChoices,
  formFontSizeTaken,
  type FormPreviewVersion,
  type TemplateSource,
} from '@/api/documentTemplates'
import FormFontRow from './FormFontRow.vue'
import FormPreviewPair from './FormPreviewPair.vue'
import OriginalFontFiles from './OriginalFontFiles.vue'

/**
 * Chooses the font each font of a Word form is drawn in that the station or association has not got,
 * its weight and its size, before the form is converted: one of the fonts it reaches, each shown with a
 * line of sample text, or the default font. Nothing is replaced without a choice: a font a built-in one
 * stands in for starts on that one, marked where it sets every letter as wide (Carlito for Calibri) rather
 * than only looking alike (Open Sans for Frutiger); every other font starts on the default font, which is
 * named. A font whose name says light starts on the light cut where the family ships one.
 *
 * <p>The form is drawn beside itself to compare: in its own fonts, with a file added for each one it does
 * not carry for that drawing only, and with the choices made. A font with neither shows its look-alike in
 * the original, which says so. A replacement whose letters are wider breaks lines elsewhere, which the size
 * makes up for.
 */
const props = defineProps<{
  missing: MissingFonts
  fonts: readonly FontFamilyOption[]
  sample: FontSampleAddress
  file: File
  source: Pick<TemplateSource, 'formPreview'>
}>()

const emit = defineEmits<{
  cancel: []
  choose: [choices: FormFontChoices]
}>()

const {t} = useI18n()
provideFontSamples(props.sample)

const chosen = ref<FormFontChoices>(Object.fromEntries(props.missing.missing.map(family => [family, firstChoice(family)])))
const originalFonts = ref<Record<string, File>>({})

const taken = computed(() => Object.values(chosen.value).every(choice => formFontSizeTaken(choice.size)))
const guessed = computed(() => props.missing.missing
  .filter(family => !props.missing.embedded.includes(family) && !originalFonts.value[family]))

/**
 * The choice a font starts on: the family offered first, else the default font, light where the name of
 * the font says so and that family ships a light cut.
 */
function firstChoice(family: string): FormFontChoice {
  const replacement = props.missing.suggested[family]?.family ?? ''
  const light = props.missing.light.includes(family)
    && props.missing.lightCuts.includes(replacement || props.missing.defaultFamily)
  return {family: replacement, size: FORM_FONT_SIZE.same, weight: light ? 'LIGHT' : 'REGULAR'}
}

/** What a picker is called: the font it replaces, and where one is offered of the same measure, that it is. */
function labelOf(family: string): string {
  const suggestion = props.missing.suggested[family]
  return suggestion?.sameMeasure
    ? t('documentTemplates.formImport.fontForSameMeasure', {family, replacement: suggestion.family})
    : t('documentTemplates.formImport.fontFor', {family})
}

function choiceOf(family: string): FormFontChoice {
  return chosen.value[family] ?? firstChoice(family)
}

function draw(version: FormPreviewVersion): Promise<Blob> {
  return props.source.formPreview(props.file, version, chosen.value, originalFonts.value)
}
</script>

<template>
  <Modal :model-value="true" size="xl" @update:model-value="emit('cancel')">
    <div class="space-y-4" data-testid="form-font-choice">
      <SubHeader>{{ t('documentTemplates.formImport.fontsTitle') }}</SubHeader>
      <MutedText tag="p">{{ t('documentTemplates.formImport.fontsHint') }}</MutedText>
      <FormFontRow v-for="family in missing.missing" :key="family" :model-value="choiceOf(family)" :label="labelOf(family)"
                   :fonts="fonts" :default-family="missing.defaultFamily" :light-cuts="missing.lightCuts"
                   :test-id="`form-font-${family}`" @update:model-value="choice => chosen[family] = choice"/>
      <OriginalFontFiles v-model="originalFonts" :missing="missing.missing" :embedded="missing.embedded"/>
      <FormPreviewPair :draw="draw" :ready="taken" :guessed="guessed"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'file-import']" :disabled="!taken" data-testid="form-font-confirm"
                       @click="emit('choose', chosen)">
          {{ t('documentTemplates.formImport.convert') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
