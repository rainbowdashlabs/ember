/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import {FormFieldKind, type FormField, type Placeholder} from '@/api/generated/schema'
import PlaceholderPicker from './PlaceholderPicker.vue'
import {withPlaceholder} from './placeholderText'

/**
 * What one form field of the PDF is filled with: a text with placeholders, or for a check box the
 * value whose yes ticks it. Left empty, the field keeps what the PDF shows.
 */
const text = defineModel<string>({required: true})

defineProps<{
  field: FormField
  placeholders: Placeholder[]
  legal: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="grid gap-2 sm:grid-cols-2 sm:items-end" data-testid="form-binding">
    <LabelledField :label="field.name" :help="t(field.kind === FormFieldKind.CHECK ? 'documentTemplates.checkWhenHelp' : 'documentTemplates.formFieldHelp')">
      <TextInput v-model="text" :placeholder="t('documentTemplates.formFieldKeeps')"/>
    </LabelledField>
    <PlaceholderPicker :placeholders="placeholders" :legal="legal" @pick="placeholder => text = withPlaceholder(text, placeholder.key)"/>
  </div>
</template>
