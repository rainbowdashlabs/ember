/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FontFamilyPicker from '@/components/documents/fonts/FontFamilyPicker.vue'
import FontPreview from '@/components/documents/fonts/FontPreview.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {FontStyle, type FontFamilyOption, type PdfField} from '@/api/generated/schema'
import {FONT_STYLES, printedStyle, sameFamily} from '@/components/documents/fonts/fontOptions'

/**
 * The font a text field prints in: a family of uploaded fonts a field can embed, or the default font,
 * and its style. A style the family lacks prints in its regular one, which the choice says.
 */
const field = defineModel<PdfField>({required: true})

const props = defineProps<{
  fonts: readonly FontFamilyOption[]
  defaultFamily: string
}>()

const {t} = useI18n()

const style = computed(() => field.value.fontStyle ?? FontStyle.REGULAR)
const family = computed(() => {
  const name = field.value.fontFamily
  return name ? props.fonts.find(option => sameFamily(option.family, name)) ?? null : null
})

function styleLabel(option: FontStyle): string {
  const label = t(`documentFonts.style.${option}`)
  return printedStyle(family.value, option) === option ? label : t('documentFonts.styleFallsBack', {style: label})
}
</script>

<template>
  <div class="space-y-3" data-testid="pdf-field-font">
    <div class="grid gap-3 sm:grid-cols-2">
      <FontFamilyPicker :model-value="field.fontFamily" :label="t('documentFonts.fieldFont')" :fonts="fonts" pdf-only
                        :default-family="defaultFamily" test-id="pdf-field-family"
                        @update:model-value="name => field = {...field, fontFamily: name}"/>
      <LabelledField :label="t('documentFonts.styleLabel')">
        <SelectInput :model-value="style" class="w-full" data-testid="pdf-field-style"
                     @update:model-value="value => field = {...field, fontStyle: value as FontStyle}">
          <option v-for="option in FONT_STYLES" :key="option" :value="option">{{ styleLabel(option) }}</option>
        </SelectInput>
      </LabelledField>
    </div>
    <FontPreview :family="field.fontFamily" :default-family="defaultFamily" :font-style="printedStyle(family, style)"/>
  </div>
</template>
