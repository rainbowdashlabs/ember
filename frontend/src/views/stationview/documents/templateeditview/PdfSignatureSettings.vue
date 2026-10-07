/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SignerSelect from '@/components/documents/SignerSelect.vue'
import type {PdfField, Placeholder} from '@/api/generated/schema'
import PlaceholderPopover from './placeholderpicker/PlaceholderPopover.vue'
import FieldTextLayout from './FieldTextLayout.vue'
import {withPlaceholder} from './placeholderText'
import type {PlaceholderChoice} from './placeholderpicker/placeholderKey'

/**
 * What a signature field on a PDF does: who signs, whether a line is drawn to sign on, and its text.
 *
 * <p>The line is left out where the PDF already has one. The text names the field in the editor and,
 * where asked to, prints under the line with its placeholders filled in, in the size and alignment set
 * here. Otherwise it stays in the editor only.
 */
const field = defineModel<PdfField>({required: true})

defineProps<{
  placeholders: Placeholder[]
  legal: boolean
}>()

const {t} = useI18n()

function setText(text: string | undefined) {
  field.value = {...field.value, text: text ?? ''}
}

function append(choice: PlaceholderChoice) {
  setText(withPlaceholder(field.value.text ?? '', choice.key))
}
</script>

<template>
  <div class="space-y-3" data-testid="pdf-signature-settings">
    <SignerSelect :model-value="field.role" @update:model-value="role => field = {...field, role: role ?? null}"/>
    <ToggleSetting :model-value="!field.withoutLine" :label="t('documentTemplates.signatureLine')"
                   :hint="t('documentTemplates.signatureLineHint')" data-testid="pdf-signature-line"
                   @update:model-value="drawn => field = {...field, withoutLine: !drawn}"/>
    <LabelledField :label="t('documentTemplates.signatureText')" :help="t('documentTemplates.signatureTextHelp')">
      <TextInput :model-value="field.text ?? ''" data-testid="pdf-field-text" @update:model-value="setText"/>
    </LabelledField>
    <PlaceholderPopover :placeholders="placeholders" :legal="legal" @pick="append"/>
    <ToggleSetting :model-value="field.printText" :label="t('documentTemplates.signaturePrintText')"
                   :hint="t('documentTemplates.signaturePrintTextHint')" data-testid="pdf-signature-print-text"
                   @update:model-value="printText => field = {...field, printText}"/>
    <FieldTextLayout v-if="field.printText" v-model="field"/>
  </div>
</template>
