/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import SingleFieldModal from '@/components/feedback/SingleFieldModal.vue'
import FileListPanel from './FileListPanel.vue'
import DeleteFileModal from './DeleteFileModal.vue'
import LoadTemplateModal from './LoadTemplateModal.vue'
import ImportDocumentModal from './ImportDocumentModal.vue'
import {adminSettings} from '@/api'
import type {LegalFile, LegalTemplate} from '@/api/adminSettings'

const {t} = useI18n()

const props = defineProps<{
  type: string
  locale: string
  placeholderValues: Record<string, string>
}>()

const emit = defineEmits<{
  error: [message: string]
  saved: []
}>()

const files = ref<LegalFile[]>([])
const loading = ref(false)
const showPreview = ref(false)

const showAddFileModal = ref(false)
const newFileName = ref('')
const showDeleteFileModal = ref(false)
const showLoadTemplateModal = ref(false)
const showImportModal = ref(false)
const fileToDeleteIndex = ref(-1)

const fileToDeleteName = computed(() => {
  if (fileToDeleteIndex.value < 0) return ''
  const file = files.value[fileToDeleteIndex.value]
  return file?.displayName || file?.filename || ''
})

async function load() {
  loading.value = true
  showPreview.value = false
  try {
    const result = await adminSettings.getLegalFiles(props.type, props.locale)
    files.value = Array.isArray(result) ? result : []
  } catch {
    files.value = []
    emit('error', t('common.error'))
  } finally {
    loading.value = false
  }
}

async function saveAll() {
  try {
    files.value = await adminSettings.saveLegalFiles(props.type, props.locale, files.value)
    emit('saved')
  } catch (e) {
    emit('error', t('common.error'))
    throw e
  }
}

function addFile() {
  const name = newFileName.value.trim().replace(/\.md$/, '')
  if (!name) return
  showAddFileModal.value = false
  newFileName.value = ''
  files.value = [...files.value, {filename: '', displayName: name, content: '', enabled: true}]
}

function applyTemplates(templates: LegalTemplate[]) {
  const next = [...files.value]
  for (const template of templates) {
    const index = next.findIndex(file => file.displayName === template.displayName)
    const existing = next[index]
    if (existing) next[index] = {...existing, content: template.content}
    else next.push({filename: '', displayName: template.displayName, content: template.content, enabled: true})
  }
  files.value = next
}

/**
 * An imported document replaces what is in the editor: it is a whole document, not a section to
 * merge in. Nothing is written until the editor is saved.
 */
function applyImport(imported: LegalFile[]) {
  files.value = imported
}

function confirmDeleteFile(index: number) {
  fileToDeleteIndex.value = index
  showDeleteFileModal.value = true
}

function deleteFile() {
  if (fileToDeleteIndex.value < 0) return
  files.value = files.value.filter((_, i) => i !== fileToDeleteIndex.value)
  showDeleteFileModal.value = false
  fileToDeleteIndex.value = -1
}

watch(() => [props.type, props.locale], load, {immediate: true})

defineExpose({reload: load})
</script>

<template>
  <div>
    <Spinner v-if="loading" size="md"/>
    <FileListPanel
        v-else
        v-model:files="files"
        v-model:show-preview="showPreview"
        :placeholder-values="placeholderValues"
        :save-action="saveAll"
        @add-file="showAddFileModal = true"
        @load-template="showLoadTemplateModal = true"
        @import-document="showImportModal = true"
        @delete-file="confirmDeleteFile"
    />

    <SingleFieldModal
        v-model:show="showAddFileModal"
        v-model:value="newFileName"
        :title="t('adminSettings.legal.addFileTitle')"
        :placeholder="t('adminSettings.legal.fileNamePlaceholder')"
        :confirm-label="t('adminSettings.legal.addFile')"
        @confirm="addFile"
    />
    <DeleteFileModal
        v-model:show="showDeleteFileModal"
        :display-name="fileToDeleteName"
        @confirm="deleteFile"
    />
    <LoadTemplateModal
        v-model:show="showLoadTemplateModal"
        :type="type"
        :locale="locale"
        :existing="files.map(file => file.displayName)"
        @load="applyTemplates"
    />
    <ImportDocumentModal
        v-model:show="showImportModal"
        :type="type"
        :locale="locale"
        @apply="applyImport"
    />
  </div>
</template>
