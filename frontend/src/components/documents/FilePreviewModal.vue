/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import FileView from '@/components/documents/FileView.vue'

/**
 * A file put in front of the reader on its own, with the button that saves it underneath.
 *
 * <p>Knowing which of four sheets is the map meant saving all four and opening them one at a time.
 * The drawing is {@link FileView}'s; what belongs here is the frame around it and the way out.
 */
defineProps<{
  /** The bytes themselves, or the endpoint to ask for them. */
  source: string | Blob
  title: string
  mimeType?: string | null
}>()

const emit = defineEmits<{close: []; download: []}>()
</script>

<template>
  <Modal :model-value="true" size="lg" @update:model-value="emit('close')">
    <div class="space-y-3" data-testid="file-preview">
      <SubHeader>{{ title }}</SubHeader>
      <FileView :source="source" :title="title" :mime-type="mimeType"/>
      <DownloadButton data-testid="file-preview-download" @click="emit('download')"/>
    </div>
  </Modal>
</template>
