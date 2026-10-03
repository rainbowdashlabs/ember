/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {FormFieldKind, type FormBinding, type FormField, type Placeholder} from '@/api/generated/schema'
import FormBindingRow from './FormBindingRow.vue'

/**
 * The form fields the PDF brings. Text fields and check boxes can be filled from placeholders; every
 * other kind keeps what it shows. All of them are flattened into the page when a document is made, so
 * nobody types into a generated form.
 *
 * <p>A field the template fills that a newer upload of the PDF no longer has is listed apart, to be
 * removed before the template can be saved.
 */
const bindings = defineModel<FormBinding[]>({required: true})

const props = defineProps<{
  formFields: FormField[]
  placeholders: Placeholder[]
  legal: boolean
}>()

const {t} = useI18n()

const fillable = computed(() => props.formFields.filter(field => field.kind === FormFieldKind.TEXT || field.kind === FormFieldKind.CHECK))
const others = computed(() => props.formFields.filter(field => !fillable.value.includes(field)).map(field => field.name))
const gone = computed(() => bindings.value.filter(binding => !props.formFields.some(field => field.name === binding.fieldName)))

function textOf(name: string): string {
  return bindings.value.find(binding => binding.fieldName === name)?.text ?? ''
}

function setText(name: string, text: string) {
  const rest = bindings.value.filter(binding => binding.fieldName !== name)
  bindings.value = text.length > 0 ? [...rest, {fieldName: name, text}] : rest
}
</script>

<template>
  <div class="space-y-3" data-testid="form-bindings">
    <SubHeader>{{ t('documentTemplates.formFieldsTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.formFieldsHint') }}</MutedText>
    <FormBindingRow v-for="field in fillable" :key="field.name" :field="field" :placeholders="placeholders" :legal="legal"
                    :model-value="textOf(field.name)" @update:model-value="text => setText(field.name, text)"/>
    <MutedText v-if="others.length > 0" size="sm" tag="p">{{ t('documentTemplates.formFieldsOther', {fields: others.join(', ')}) }}</MutedText>
    <Alert v-for="binding in gone" :key="binding.fieldName" variant="error">
      <div class="flex items-center justify-between gap-2">
        <span>{{ t('documentTemplates.formFieldGone', {field: binding.fieldName}) }}</span>
        <DeleteButton @click="setText(binding.fieldName, '')"/>
      </div>
    </Alert>
  </div>
</template>
