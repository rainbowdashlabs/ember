/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onUnmounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import client from '@/api/client'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import IconButton from '@/components/button/IconButton.vue'
import PdfCanvas from '@/components/documents/PdfCanvas.vue'
import {fileKindOf} from '@/util/fileKind'

/**
 * A file drawn where it is, rather than saved to be looked at.
 *
 * <p>The bytes either come from an endpoint that wants a token or are handed over already, which is
 * what lets an export be read the moment it is built instead of fetched back from the server that
 * just sent it. Either way they become an object URL that lives exactly as long as this does: a file
 * shown to somebody who may see it must not outlive their looking at it as an address anybody could
 * follow.
 *
 * <p>A PDF is drawn page by page rather than handed to the browser in a frame, because a phone has no
 * viewer behind a frame and shows an empty box instead of the document.
 */
const props = defineProps<{
  /** The bytes themselves, or the endpoint to ask for them. */
  source: string | Blob
  title: string
  mimeType?: string | null
}>()

const {t} = useI18n()

const objectUrl = ref<string | null>(null)
const pdfBytes = ref<Blob | null>(null)
const pageCount = ref(0)
const page = ref(1)
const text = ref<string | null>(null)
const truncated = ref(false)
const loading = ref(false)
const failed = ref(false)

const kind = computed(() => fileKindOf(props.mimeType))

function revoke() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = null
  }
}

/** How much of a text file is worth putting on screen before it stops being a preview. */
const TEXT_PREVIEW_BYTES = 200_000

/** Which request the content on screen belongs to, so a closed view leaves nothing behind. */
let current = 0

async function bytes(): Promise<Blob> {
  if (props.source instanceof Blob) return props.source
  const res = await client.get(props.source, {responseType: 'blob'})
  return res.data as Blob
}

async function load() {
  const mine = ++current
  revoke()
  text.value = null
  pdfBytes.value = null
  pageCount.value = 0
  page.value = 1
  failed.value = false
  if (kind.value === 'other') return
  loading.value = true
  try {
    const blob = await bytes()
    if (mine !== current) return
    if (kind.value === 'text') {
      const head = await blob.slice(0, TEXT_PREVIEW_BYTES).text()
      if (mine !== current) return
      truncated.value = blob.size > TEXT_PREVIEW_BYTES
      text.value = head
    } else if (kind.value === 'pdf') {
      pdfBytes.value = blob
      return
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

/** The document is open only once its pages have been counted, which is where the spinner ends. */
function pdfOpened(count: number) {
  pageCount.value = count
  loading.value = false
}

function pdfFailed() {
  failed.value = true
  loading.value = false
}

function turnTo(wanted: number) {
  if (wanted < 1 || wanted > pageCount.value) return
  page.value = wanted
}

watch(() => props.source, load, {immediate: true})
onUnmounted(() => {
  current++
  revoke()
})
</script>

<template>
  <div class="min-h-40 space-y-3" data-testid="file-view">
    <Spinner v-if="loading" size="md"/>
    <EmptyHint v-else-if="failed">{{ t('common.error') }}</EmptyHint>
    <EmptyHint v-else-if="kind === 'other'">{{ t('files.noPreview') }}</EmptyHint>

    <img
        v-else-if="kind === 'image' && objectUrl"
        :src="objectUrl"
        :alt="title"
        class="max-h-[70vh] w-full object-contain rounded-theme"
    />
    <video
        v-else-if="kind === 'video' && objectUrl"
        :src="objectUrl"
        class="max-h-[70vh] w-full rounded-theme"
        controls
    />
    <audio v-else-if="kind === 'audio' && objectUrl" :src="objectUrl" class="w-full" controls/>
    <pre
        v-else-if="kind === 'text' && text !== null"
        class="max-h-[70vh] overflow-auto rounded-theme bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 p-3 text-xs whitespace-pre-wrap break-words"
    >{{ text }}</pre>

    <div
        v-if="kind === 'pdf' && pdfBytes && !failed"
        v-show="!loading"
        class="h-[70vh] w-full flex items-center justify-center rounded-theme border border-bg-light-accent dark:border-bg-dark-accent"
    >
      <PdfCanvas
          :source="pdfBytes"
          :page="page"
          class="max-w-full max-h-full"
          @loaded="pdfOpened"
          @failed="pdfFailed"
      />
    </div>

    <div v-if="pageCount > 1" class="flex items-center justify-center gap-4" data-testid="file-view-pages">
      <IconButton
          :icon="['fas', 'chevron-left']"
          :label="t('common.previous')"
          :disabled="page <= 1"
          @click="turnTo(page - 1)"
      />
      <span class="text-sm">{{ page }} / {{ pageCount }}</span>
      <IconButton
          :icon="['fas', 'chevron-right']"
          :label="t('common.next')"
          :disabled="page >= pageCount"
          @click="turnTo(page + 1)"
      />
    </div>

    <p v-if="truncated" class="text-xs text-(--text-muted)">{{ t('files.truncated') }}</p>
  </div>
</template>
