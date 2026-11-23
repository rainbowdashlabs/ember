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
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import LoadTemplateModal from './LoadTemplateModal.vue'
import ImportDocumentModal from './ImportDocumentModal.vue'
import {adminSettings} from '@/api'
import type {LegalFileEntry, TemplateSection} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {describeFailure, type Failure} from '@/util/failure'

const {t} = useI18n()

const props = defineProps<{
  type: string
  locale: string
  placeholderValues: Record<string, string>
}>()

const emit = defineEmits<{
  error: [failure: Failure]
  saved: []
}>()

const files = ref<LegalFileEntry[]>([])
const showPreview = ref(false)

const showAddFileModal = ref(false)
const newFileName = ref('')
const showLoadTemplateModal = ref(false)
const showImportModal = ref(false)
/** The file asked about, held by its position, since a position of nought would read as none. */
interface FilePosition {
  index: number
}

const {show: showDeleteFileModal, target: fileToDelete, requestDelete: requestDeleteFile, confirm: deleteFile} =
  useConfirmDelete<FilePosition>({
    onDelete: async ({index}) => {
      files.value = files.value.filter((_, i) => i !== index)
    },
  })

const fileToDeleteName = computed(() => {
  if (!fileToDelete.value) return ''
  const file = files.value[fileToDelete.value.index]
  return file?.displayName || file?.filename || ''
})

const {loading, failure, reload: fetchFiles} = useAsyncLoader(async (isCurrent) => {
  let result: LegalFileEntry[]
  try {
    result = await adminSettings.getLegalFiles(props.type, props.locale)
  } catch (e) {
    if (isCurrent()) files.value = []
    throw e
  }
  if (isCurrent()) files.value = Array.isArray(result) ? result : []
}, {autoLoad: false})

/** Reads the files of this document again and hands a failure to the page, which shows it. */
async function load() {
  showPreview.value = false
  await fetchFiles()
  if (failure.value) emit('error', failure.value)
}

async function saveAll() {
  try {
    files.value = await adminSettings.saveLegalFiles(props.type, props.locale, files.value)
    emit('saved')
  } catch (e) {
    emit('error', describeFailure(e, t))
    throw e
  }
}

function addFile() {
  const name = newFileName.value.trim().replace(/\.md$/, '')
  if (!name) return
  showAddFileModal.value = false
  newFileName.value = ''
  files.value = [...files.value, {filename: '', displayName: name, content: '', enabled: true, generated: false}]
}

function applyTemplates(templates: TemplateSection[]) {
  const next = [...files.value]
  for (const template of templates) {
    const index = next.findIndex(file => file.displayName === template.displayName)
    const existing = next[index]
    if (existing) next[index] = {...existing, content: template.content}
    else next.push({
      filename: '',
      displayName: template.displayName,
      content: template.content,
      enabled: true,
      generated: false,
    })
  }
  files.value = next
}

/**
 * An imported document replaces what is in the editor: it is a whole document, not a section to
 * merge in. Nothing is written until the editor is saved.
 */
function applyImport(imported: LegalFileEntry[]) {
  files.value = imported
}

function confirmDeleteFile(index: number) {
  requestDeleteFile({index})
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
    <ConfirmDeleteModal
        v-model="showDeleteFileModal"
        :title="t('adminSettings.legal.deleteFileTitle')"
        :message="t('adminSettings.legal.deleteFileConfirm', {name: fileToDeleteName})"
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
