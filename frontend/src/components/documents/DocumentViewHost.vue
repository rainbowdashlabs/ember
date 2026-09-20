/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FilePreviewModal from '@/components/documents/FilePreviewModal.vue'
import {clearUnsavedDocument, closeDocument, getUnsavedDocument, getViewedDocument} from '@/util/documentView'
import {SaveResult, saveBlob} from '@/util/saveBlob'
import {showToast} from '@/util/toast'

/**
 * The one place a finished document is read, sitting beside the toasts for the same reason they do.
 *
 * <p>An export is built wherever its button is, and every one of those places would otherwise carry a
 * modal of its own to show it in. One host means a new export needs no markup at all: it hands the
 * document over and this draws it.
 */
const {t} = useI18n()

const viewed = getViewedDocument()
const unsaved = getUnsavedDocument()

async function save() {
  const document = viewed.value
  if (!document) return
  if (await saveBlob(document.blob, document.filename) === SaveResult.UNAVAILABLE) {
    showToast(t('files.saveUnavailable'), 'error')
  }
}

watch(unsaved, filename => {
  if (!filename) return
  showToast(t('files.saveUnavailable'), 'error')
  clearUnsavedDocument()
})
</script>

<template>
  <FilePreviewModal
      v-if="viewed"
      :source="viewed.blob"
      :title="viewed.filename"
      :mime-type="viewed.mimeType"
      @close="closeDocument"
      @download="save"
  />
</template>
