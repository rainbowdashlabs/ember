/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import FilePreviewModal from '@/components/documents/FilePreviewModal.vue'
import {closeDocument, getViewedDocument} from '@/util/documentView'
import {saveBlob} from '@/util/downloadAuthed'

/**
 * The one place a finished document is read, sitting beside the toasts for the same reason they do.
 *
 * <p>An export is built wherever its button is, and every one of those places would otherwise carry a
 * modal of its own to show it in. One host means a new export needs no markup at all: it hands the
 * document over and this draws it.
 */
const viewed = getViewedDocument()

function save() {
  const document = viewed.value
  if (document) saveBlob(document.blob, document.filename)
}
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
