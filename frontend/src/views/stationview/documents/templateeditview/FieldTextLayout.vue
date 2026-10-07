/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {TextAlign, type PdfField} from '@/api/generated/schema'
import {DEFAULT_FONT_SIZE} from './pdfFields'

/**
 * The size of a field's text in points and where it sits across the field, for a text field and for
 * the text a signature field prints under its line.
 */
const field = defineModel<PdfField>({required: true})

const {t} = useI18n()

const aligns = [TextAlign.LEFT, TextAlign.CENTER, TextAlign.RIGHT]
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2">
    <LabelledField :label="t('documentTemplates.fontSize')">
      <NumberInput :model-value="field.fontSize"
                   @update:model-value="size => field = {...field, fontSize: size ?? DEFAULT_FONT_SIZE}"/>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.alignLabel')">
      <SelectInput :model-value="field.align" @update:model-value="align => field = {...field, align: align as TextAlign}">
        <option v-for="align in aligns" :key="align" :value="align">{{ t(`documentTemplates.align.${align}`) }}</option>
      </SelectInput>
    </LabelledField>
  </div>
</template>
