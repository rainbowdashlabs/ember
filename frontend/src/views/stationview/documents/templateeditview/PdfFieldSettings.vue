/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import {PdfFieldKind, type FontFamilyOption, type PdfField, type Placeholder} from '@/api/generated/schema'
import FieldFontSettings from './FieldFontSettings.vue'
import FieldTextLayout from './FieldTextLayout.vue'
import PdfSignatureSettings from './PdfSignatureSettings.vue'
import PlaceholderPopover from './placeholderpicker/PlaceholderPopover.vue'
import {withPlaceholder} from './placeholderText'
import type {PlaceholderChoice} from './placeholderpicker/placeholderKey'

/**
 * What the chosen field does: the text it prints and how, the value whose yes ticks it, or who signs
 * in it and what goes with the signature ({@link PdfSignatureSettings}). Where it sits is set on the
 * page itself.
 */
const field = defineModel<PdfField>({required: true})

defineProps<{
  placeholders: Placeholder[]
  legal: boolean
  fonts: readonly FontFamilyOption[]
  defaultFamily: string
}>()

const emit = defineEmits<{
  remove: []
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
  <div class="space-y-3 rounded-lg border border-(--border) p-3" data-testid="pdf-field-settings">
    <div class="flex items-center justify-between gap-2">
      <SecondaryBadge>{{ t(`documentTemplates.fieldKind.${field.kind}`) }}</SecondaryBadge>
      <DeleteButton @click="emit('remove')"/>
    </div>
    <PdfSignatureSettings v-if="field.kind === PdfFieldKind.SIGNATURE" v-model="field" :placeholders="placeholders"
                          :legal="legal"/>
    <template v-else>
      <LabelledField :label="t(field.kind === PdfFieldKind.CHECK ? 'documentTemplates.checkWhen' : 'documentTemplates.fieldText')"
                     :help="t(field.kind === PdfFieldKind.CHECK ? 'documentTemplates.checkWhenHelp' : 'documentTemplates.fieldTextHelp')">
        <TextInput :model-value="field.text ?? ''" data-testid="pdf-field-text" @update:model-value="setText"/>
      </LabelledField>
      <PlaceholderPopover :placeholders="placeholders" :legal="legal" @pick="append"/>
    </template>
    <template v-if="field.kind === PdfFieldKind.TEXT">
      <FieldTextLayout v-model="field"/>
      <ToggleSetting :model-value="field.wrap" :label="t('documentTemplates.wrap')" :hint="t('documentTemplates.wrapHint')"
                     @update:model-value="wrap => field = {...field, wrap}"/>
      <FieldFontSettings v-model="field" :fonts="fonts" :default-family="defaultFamily"/>
    </template>
  </div>
</template>
