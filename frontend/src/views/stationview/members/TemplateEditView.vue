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
import {showToast} from '@/util/toast'
import {useTemplateEditor} from './templateeditview/useTemplateEditor'
import GeneralPanel from './templateeditview/GeneralPanel.vue'
import LetterheadPanel from './templateeditview/LetterheadPanel.vue'
import BodyPanel from './templateeditview/BodyPanel.vue'
import PronounPanel from './templateeditview/PronounPanel.vue'
import SelfServicePanel from './templateeditview/SelfServicePanel.vue'
import PreviewPanel from './templateeditview/PreviewPanel.vue'
import EditorActions from './templateeditview/EditorActions.vue'

/**
 * Writes one letter template: what it is called and how its documents are filed, the letterhead, the
 * body with its placeholders, the pronouns, self service, and a look at the result for any member.
 *
 * <p>The address names the template, or `new` for one that does not exist yet; saving a new one moves
 * the address to it, so a reload opens what was just saved.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const templateId = computed(() => {
  const id = Number(route.params.id)
  return Number.isFinite(id) && id > 0 ? id : null
})

const editor = useTemplateEditor(templateId)
const {draft, saved, loader, saving, archiving} = editor

const tab = ref('general')
const tabs = computed(() => [
  {key: 'general', label: t('documentTemplates.tabGeneral')},
  {key: 'letterhead', label: t('documentTemplates.tabLetterhead')},
  {key: 'body', label: t('documentTemplates.tabBody')},
  {key: 'pronouns', label: t('documentTemplates.tabPronouns')},
  {key: 'selfService', label: t('documentTemplates.tabSelfService')},
  {key: 'preview', label: t('documentTemplates.tabPreview')},
])

const pageTitle = computed(() => saved.value
    ? t('pages.member-document-template-edit.titleNamed', {name: saved.value.name})
    : t('pages.member-document-template-edit.title'))

async function save() {
  const written = await saving.run()
  if (!written) return
  showToast(t('documentTemplates.saved'), 'success')
  if (templateId.value !== written.id) {
    await router.replace({name: 'member-document-template-edit', params: {id: written.id}})
  }
}

async function setArchived(archived: boolean) {
  const written = await archiving.run(archived)
  if (written) showToast(t(archived ? 'documentTemplates.archived' : 'documentTemplates.restored'), 'success')
}
</script>

<template>
  <ViewContent :title="pageTitle" :subtitle="t('pages.member-document-template-edit.subtitle')">
    <Spinner v-if="loader.loading.value" size="lg"/>
    <FailureAlert :failure="loader.failure.value ?? saving.failure.value ?? archiving.failure.value"/>
    <div v-if="!loader.loading.value && !loader.failure.value" class="space-y-6">
      <EditorActions
          :saved="saved"
          :saving="saving.running.value"
          :can-save="draft.name.trim().length > 0"
          @save="save"
          @archive="setArchived"
      />
      <TabBar v-model="tab" :tabs="tabs"/>
      <GeneralPanel v-if="tab === 'general'" v-model="draft" :document-tags="editor.documentTags.value"/>
      <LetterheadPanel v-if="tab === 'letterhead'" v-model="draft" :placeholders="editor.placeholders.value"/>
      <BodyPanel v-if="tab === 'body'" v-model="draft" :placeholders="editor.placeholders.value" :labels="editor.labels.value"/>
      <PronounPanel v-if="tab === 'pronouns'" v-model="draft" :choice-fields="editor.choiceFields.value"/>
      <SelfServicePanel
          v-if="tab === 'selfService'"
          v-model="draft"
          :groups="editor.groups.value"
          :tags="editor.tags.value"
          :members="editor.members.value"
      />
      <PreviewPanel v-if="tab === 'preview'" :draft="draft" :saved="saved" :members="editor.members.value"/>
    </div>
  </ViewContent>
</template>
