/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {
  DocumentTemplateResponse,
  FontFamilyOption,
  PdfField,
  PdfFieldKind,
  Placeholder,
} from '@/api/generated/schema'
import FormBindingsPanel from './FormBindingsPanel.vue'
import PdfFieldCanvas from './PdfFieldCanvas.vue'
import PdfFieldSettings from './PdfFieldSettings.vue'
import PdfFieldToolbar from './PdfFieldToolbar.vue'
import PdfUploadField from './PdfUploadField.vue'
import {newField} from './pdfFields'
import type {PageGeometry} from './pdfViewport'
import type {TemplateDraft} from './templateDraft'

/**
 * The PDF of a PDF template and what is laid over it: its pages with the fields placed on them, the
 * settings of the field chosen, and the PDF's own form fields.
 *
 * <p>Fields are drawn and moved on the page as the reader sees it, however the page is turned. A
 * field left on a page that a newer upload of the PDF no longer has is named, so it can be moved or
 * removed before the template is saved again.
 */
const draft = defineModel<TemplateDraft>({required: true})

const props = defineProps<{
  saved: DocumentTemplateResponse | null
  source: Blob | null
  placeholders: Placeholder[]
  labels: ReadonlyMap<string, string>
  fonts: readonly FontFamilyOption[]
}>()

const emit = defineEmits<{
  upload: [file: File]
}>()

const {t} = useI18n()

const page = ref(1)
const pageCount = ref(0)
const selected = ref<number | null>(null)
const geometry = ref<PageGeometry | null>(null)

const pdf = computed(() => props.saved?.pdf ?? null)
const pageTotal = computed(() => pdf.value?.inspection.pages.length ?? 0)
const stranded = computed(() => draft.value.fields.filter(field => field.rect.page > pageTotal.value).length)
const chosen = computed(() => selected.value === null ? null : draft.value.fields[selected.value] ?? null)

watch(() => pdf.value?.id, () => {
  page.value = 1
  selected.value = null
})

function turnTo(number: number) {
  page.value = number
  selected.value = null
}

function add(kind: PdfFieldKind) {
  if (!geometry.value) return
  draft.value.fields = [...draft.value.fields, newField(kind, page.value, geometry.value, draft.value.fields)]
  selected.value = draft.value.fields.length - 1
}

function change(index: number, field: PdfField) {
  draft.value.fields = draft.value.fields.map((current, at) => at === index ? field : current)
}

function removeStranded() {
  draft.value.fields = draft.value.fields.filter(field => field.rect.page <= pageTotal.value)
  selected.value = null
}

function changeChosen(field: PdfField) {
  if (selected.value !== null) change(selected.value, field)
}

function removeChosen() {
  const index = selected.value
  if (index === null) return
  draft.value.fields = draft.value.fields.filter((_, at) => at !== index)
  selected.value = null
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('documentTemplates.pdfTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.pdfHint') }}</MutedText>
    <PdfUploadField :pdf="pdf" :can-upload="!!saved" @select="file => emit('upload', file)"/>
    <Alert v-if="stranded > 0" variant="error" data-testid="pdf-fields-stranded">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <span>{{ t('documentTemplates.fieldsStranded', {count: stranded}) }}</span>
        <SecondaryButton :icon="['fas', 'trash']" @click="removeStranded">{{ t('documentTemplates.removeStranded') }}</SecondaryButton>
      </div>
    </Alert>
    <template v-if="source">
      <PdfFieldToolbar :page="page" :page-count="pageCount" :can-add="!!geometry" @page="turnTo" @add="add"/>
      <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <PdfFieldCanvas :source="source" :page="page" :fields="draft.fields" :selected="selected" :labels="labels"
                        @loaded="count => pageCount = count" @drawn="drawn => geometry = drawn"
                        @select="index => selected = index" @change="change"/>
        <PdfFieldSettings v-if="chosen" :model-value="chosen" :placeholders="placeholders" :legal="draft.legal"
                          :fonts="fonts" @update:model-value="changeChosen" @remove="removeChosen"/>
        <MutedText v-else size="sm" tag="p">{{ t('documentTemplates.chooseField') }}</MutedText>
      </div>
      <FormBindingsPanel v-if="pdf && pdf.inspection.formFields.length > 0" v-model="draft.formBindings"
                         :form-fields="pdf.inspection.formFields" :placeholders="placeholders" :legal="draft.legal"/>
    </template>
  </NeutralContainer>
</template>
