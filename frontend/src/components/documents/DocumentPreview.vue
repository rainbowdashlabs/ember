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
import {contentUrl, isPreviewable, type MemberDocument} from '@/api/memberDocuments'

/**
 * The document itself, shown rather than only offered.
 *
 * <p>Images and documents with pages are handed to the browser as an object URL, which is what the
 * knowledge base does for the same reason: the bytes come from an endpoint that wants a token, so
 * they cannot simply be pointed at.
 */
const props = defineProps<{ document: MemberDocument }>()

const {t} = useI18n()

const objectUrl = ref<string | null>(null)
const text = ref<string | null>(null)
const loading = ref(false)
const failed = ref(false)

const kind = computed(() => {
  const mime = props.document.mimeType ?? ''
  if (mime.startsWith('image/')) return 'image'
  if (mime === 'application/pdf') return 'pdf'
  if (mime.startsWith('text/')) return 'text'
  return 'none'
})

function revoke() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = null
  }
}

async function load() {
  revoke()
  text.value = null
  failed.value = false
  if (!isPreviewable(props.document.mimeType)) return

  loading.value = true
  try {
    const res = await client.get(contentUrl(props.document.id), {responseType: 'blob'})
    if (kind.value === 'text') {
      text.value = await (res.data as Blob).text()
    } else {
      objectUrl.value = URL.createObjectURL(res.data as Blob)
    }
  } catch {
    failed.value = true
  }
  loading.value = false
}

watch(() => props.document.id, load, {immediate: true})
onUnmounted(revoke)
</script>

<template>
  <div class="min-h-40">
    <Spinner v-if="loading" size="md"/>
    <EmptyHint v-else-if="failed">{{ t('common.error') }}</EmptyHint>
    <EmptyHint v-else-if="kind === 'none'">{{ t('documents.noPreview') }}</EmptyHint>
    <img
        v-else-if="kind === 'image' && objectUrl"
        :src="objectUrl"
        :alt="props.document.title"
        class="max-h-[70vh] w-full object-contain rounded-theme"
    />
    <iframe
        v-else-if="kind === 'pdf' && objectUrl"
        :src="objectUrl"
        :title="props.document.title"
        class="w-full h-[70vh] rounded-theme border border-bg-light-accent dark:border-bg-dark-accent"
    />
    <pre
        v-else-if="kind === 'text' && text !== null"
        class="max-h-[70vh] overflow-auto rounded-theme bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 p-3 text-xs whitespace-pre-wrap break-words"
    >{{ text }}</pre>
  </div>
</template>
