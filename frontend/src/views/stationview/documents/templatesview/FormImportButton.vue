/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, shallowRef} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {FontFamilyOption, MissingFonts, WordFormImport} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import type {TemplateScreens} from '../templateScreens'
import FormFontChoice from './FormFontChoice.vue'
import FormImportReport from './FormImportReport.vue'

/**
 * Makes a PDF template from a Word form. The form's fields become the fields of the template: filled
 * from the member's data where their label is recognised, asked of the person generating everywhere
 * else. Where the form names fonts the owner has not got, a replacement is chosen for each before the
 * form is converted. Converting takes a few seconds, so the button says so while it runs; afterwards a
 * report says what was recognised and leads into the editor.
 */
const props = defineProps<{
  screens: TemplateScreens
}>()

const {t} = useI18n()
const router = useRouter()
const picker = ref<HTMLInputElement | null>(null)
const pending = shallowRef<File | null>(null)
const missing = shallowRef<MissingFonts | null>(null)
const fonts = shallowRef<readonly FontFamilyOption[]>([])
const report = ref<WordFormImport | null>(null)

const checking = useAsyncAction(async (file: File) => {
  const found = await props.screens.source.formFonts(file)
  if (found.missing.length === 0) {
    await importing.run(file, {})
    return
  }
  fonts.value = (await props.screens.fonts.list()).reachable
  pending.value = file
  missing.value = found
})

const importing = useAsyncAction(async (file: File, replacements: Record<string, string>) => {
  report.value = await props.screens.source.importForm(file, replacements)
})

function chosen(event: Event) {
  const input = event.target as HTMLInputElement
  const [file] = Array.from(input.files ?? [])
  input.value = ''
  if (file) void checking.run(file)
}

function convert(replacements: Record<string, string>) {
  const file = pending.value
  missing.value = null
  pending.value = null
  if (file) void importing.run(file, replacements)
}

function cancel() {
  missing.value = null
  pending.value = null
}

function open() {
  if (!report.value) return
  void router.push({name: props.screens.editRoute, params: {id: report.value.template.id}})
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <SecondaryButton :icon="['fas', 'file-import']" :disabled="checking.running.value || importing.running.value"
                     data-testid="template-import-form" @click="picker?.click()">
      {{ importing.running.value ? t('documentTemplates.formImport.running') : t('documentTemplates.formImport.button') }}
    </SecondaryButton>
    <input ref="picker" type="file" accept=".docx" class="hidden" aria-hidden="true" data-testid="template-import-form-file"
           @change="chosen"/>
    <FailureAlert :failure="checking.failure.value ?? importing.failure.value"/>
    <FormFontChoice v-if="missing" :missing="missing" :fonts="fonts" :sample="screens.fonts.sample"
                    @cancel="cancel" @choose="convert"/>
    <FormImportReport v-if="report" :report="report" @close="report = null" @open="open"/>
  </div>
</template>
