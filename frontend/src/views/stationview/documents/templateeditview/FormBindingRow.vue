/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import {FormFieldKind, type FormField, type Placeholder} from '@/api/generated/schema'
import PlaceholderTextInput from './placeholderpicker/PlaceholderTextInput.vue'

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
  <LabelledField :label="field.name" :help="t(field.kind === FormFieldKind.CHECK ? 'documentTemplates.checkWhenHelp' : 'documentTemplates.formFieldHelp')"
                 data-testid="form-binding">
    <PlaceholderTextInput v-model="text" :placeholders="placeholders" :legal="legal" :prompt="t('documentTemplates.formFieldKeeps')"/>
  </LabelledField>
</template>
