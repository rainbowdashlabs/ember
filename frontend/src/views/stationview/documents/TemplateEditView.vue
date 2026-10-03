/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import {DocumentTemplateKind} from '@/api/generated/schema'
import {showToast} from '@/util/toast'
import {useTemplateEditor} from './templateeditview/useTemplateEditor'
import GeneralPanel from './templateeditview/GeneralPanel.vue'
import LetterPanel from './templateeditview/LetterPanel.vue'
import PdfPanel from './templateeditview/PdfPanel.vue'
import SelfServicePanel from './templateeditview/SelfServicePanel.vue'
import PreviewPanel from './templateeditview/PreviewPanel.vue'
import EditorActions from './templateeditview/EditorActions.vue'
import type {TemplateScreens} from './templateScreens'

/**
 * Writes one template of a station or an association. A letter has its header, body and footer,
 * written as blocks; a PDF template has its uploaded PDF with the fields placed on it. Both have what
 * they are called, their language and how their documents are filed, self service, and a look at the
 * result: for any member of a station, and with its placeholders shown by name for an association,
 * which has no members of its own.
 *
 * <p>The address names the template, or `new` for one that does not exist yet, with `?kind=PDF` for a
 * PDF template; saving a new one moves the address to it, so a reload opens what was just saved.
 */
const props = defineProps<{
  screens: TemplateScreens
}>()

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const templateId = computed(() => {
  const id = Number(route.params.id)
  return Number.isFinite(id) && id > 0 ? id : null
})
const newKind = computed(() => route.query.kind === DocumentTemplateKind.PDF ? DocumentTemplateKind.PDF : DocumentTemplateKind.LETTER)

const editor = useTemplateEditor(templateId, newKind, props.screens)
const {draft, saved, loader, saving, archiving, uploading} = editor

const isPdf = computed(() => draft.value.kind === DocumentTemplateKind.PDF)
const tab = ref('general')
const tabs = computed(() => [
  {key: 'general', label: t('documentTemplates.tabGeneral')},
  ...(isPdf.value
    ? [{key: 'pdf', label: t('documentTemplates.tabPdf')}]
    : [{key: 'letter', label: t('documentTemplates.tabLetter')}]),
  {key: 'selfService', label: t('documentTemplates.tabSelfService')},
  {key: 'preview', label: t('documentTemplates.tabPreview')},
])

const pageTitle = computed(() => saved.value
    ? t(`pages.${props.screens.editRoute}.titleNamed`, {name: saved.value.name})
    : t(`pages.${props.screens.editRoute}.title`))

const failure = computed(() => loader.failure.value ?? saving.failure.value ?? archiving.failure.value
    ?? uploading.failure.value ?? editor.pdfLoader.failure.value)

async function save() {
  const written = await saving.run()
  if (!written) return
  showToast(t('documentTemplates.saved'), 'success')
  if (templateId.value !== written.id) {
    await router.replace({name: props.screens.editRoute, params: {id: written.id}})
  }
}

async function setArchived(archived: boolean) {
  const written = await archiving.run(archived)
  if (written) showToast(t(archived ? 'documentTemplates.archived' : 'documentTemplates.restored'), 'success')
}

async function upload(file: File) {
  const written = await uploading.run(file)
  if (written) showToast(t('documentTemplates.pdfUploaded'), 'success')
}
</script>

<template>
  <ViewContent :title="pageTitle" :subtitle="t(`pages.${screens.editRoute}.subtitle`)">
    <Spinner v-if="loader.loading.value" size="lg"/>
    <FailureAlert :failure="failure"/>
    <div v-if="!loader.loading.value && !loader.failure.value" class="space-y-6">
      <EditorActions
          :saved="saved"
          :saving="saving.running.value"
          :can-save="draft.name.trim().length > 0"
          :list-route="screens.listRoute"
          @save="save"
          @archive="setArchived"
      />
      <TabBar v-model="tab" :tabs="tabs"/>
      <GeneralPanel v-if="tab === 'general'" v-model="draft" :document-tags="editor.documentTags.value"
                    :placeholders="editor.placeholders.value"/>
      <LetterPanel v-if="tab === 'letter'" v-model="draft" :catalogue="editor.letterCatalogue.value"
                   :fonts="editor.fonts.value" :source="screens.source"/>
      <PdfPanel v-if="tab === 'pdf'" v-model="draft" :saved="saved" :source="editor.pdf.value"
                :placeholders="editor.placeholders.value" :labels="editor.labels.value" :fonts="editor.fonts.value"
                @upload="upload"/>
      <SelfServicePanel
          v-if="tab === 'selfService'"
          v-model="draft"
          :groups="editor.groups.value"
          :tags="editor.tags.value"
          :members="editor.members.value"
          :chooses-audience="screens.hasMembers"
      />
      <PreviewPanel v-if="tab === 'preview'" :draft="draft" :saved="saved" :members="editor.members.value"
                    :source="screens.source" :has-members="screens.hasMembers"/>
    </div>
  </ViewContent>
</template>
