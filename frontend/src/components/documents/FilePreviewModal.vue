/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onUnmounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import client from '@/api/client'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import {fileKindOf} from '@/util/fileKind'

/**
 * A file shown where it is, rather than saved to be looked at.
 *
 * <p>Knowing which of four sheets is the map meant saving all four and opening them one at a time.
 * The kinds a browser can draw are drawn here, and the rest say so plainly and keep the button that
 * saves them, which is the only thing that ever worked for them.
 *
 * <p>The bytes come from an endpoint that wants a token, so they are fetched and handed over as an
 * object URL. That URL lives exactly as long as the modal does: a file shown to somebody who may see
 * it must not outlive their looking at it as an address anybody could follow.
 */
const props = defineProps<{
  /** Where the bytes come from. */
  url: string
  title: string
  mimeType?: string | null
}>()

const emit = defineEmits<{close: []; download: []}>()

const {t} = useI18n()

const objectUrl = ref<string | null>(null)
const text = ref<string | null>(null)
const truncated = ref(false)
const loading = ref(false)
const failed = ref(false)

function revoke() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = null
  }
}

/** How much of a text file is worth putting on screen before it stops being a preview. */
const TEXT_PREVIEW_BYTES = 200_000

/** Which request the content on screen belongs to, so a closed modal leaves nothing behind. */
let current = 0

async function load() {
  const mine = ++current
  revoke()
  text.value = null
  failed.value = false
  if (fileKindOf(props.mimeType) === 'other') return
  loading.value = true
  try {
    const res = await client.get(props.url, {responseType: 'blob'})
    const blob = res.data as Blob
    if (mine !== current) return
    if (fileKindOf(props.mimeType) === 'text') {
      const head = await blob.slice(0, TEXT_PREVIEW_BYTES).text()
      if (mine !== current) return
      truncated.value = blob.size > TEXT_PREVIEW_BYTES
      text.value = head
    } else {
      const url = URL.createObjectURL(blob)
      if (mine !== current) {
        URL.revokeObjectURL(url)
        return
      }
      objectUrl.value = url
    }
  } catch {
    if (mine === current) failed.value = true
  }
  if (mine === current) loading.value = false
}

watch(() => props.url, load, {immediate: true})
onUnmounted(() => {
  current++
  revoke()
})
</script>

<template>
  <Modal :model-value="true" size="lg" @update:model-value="emit('close')">
    <div class="min-h-40 space-y-3" data-testid="file-preview">
      <SubHeader>{{ title }}</SubHeader>
      <Spinner v-if="loading" size="md"/>
      <EmptyHint v-else-if="failed">{{ t('common.error') }}</EmptyHint>
      <EmptyHint v-else-if="fileKindOf(mimeType) === 'other'">{{ t('files.noPreview') }}</EmptyHint>

      <img
          v-else-if="fileKindOf(mimeType) === 'image' && objectUrl"
          :src="objectUrl"
          :alt="title"
          class="max-h-[70vh] w-full object-contain rounded-theme"
      />
      <iframe
          v-else-if="fileKindOf(mimeType) === 'pdf' && objectUrl"
          :src="objectUrl"
          :title="title"
          class="w-full h-[70vh] rounded-theme border border-bg-light-accent dark:border-bg-dark-accent"
      />
      <video
          v-else-if="fileKindOf(mimeType) === 'video' && objectUrl"
          :src="objectUrl"
          class="max-h-[70vh] w-full rounded-theme"
          controls
      />
      <audio v-else-if="fileKindOf(mimeType) === 'audio' && objectUrl" :src="objectUrl" class="w-full" controls/>
      <pre
          v-else-if="fileKindOf(mimeType) === 'text' && text !== null"
          class="max-h-[70vh] overflow-auto rounded-theme bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 p-3 text-xs whitespace-pre-wrap"
      >{{ text }}</pre>
      <p v-if="truncated" class="text-xs text-(--text-muted)">{{ t('files.truncated') }}</p>

      <DownloadButton data-testid="file-preview-download" @click="emit('download')"/>
    </div>
  </Modal>
</template>
