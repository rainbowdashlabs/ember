/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, onUnmounted, ref, watch} from 'vue'
import client from '@/api/client'
import {isKbImageSrc} from '@/util/normalizeAuthSrc'

const props = defineProps<{
  html: string
}>()

const container = ref<HTMLDivElement | null>(null)
const blobUrls = new Set<string>()

function revokeAll() {
  for (const url of blobUrls) URL.revokeObjectURL(url)
  blobUrls.clear()
}

async function loadAuthImages() {
  if (!container.value) return
  const images = Array.from(container.value.querySelectorAll<HTMLImageElement>('img'))
  await Promise.all(images.map(async (img) => {
    const originalSrc = img.getAttribute('src') ?? ''
    if (!isKbImageSrc(originalSrc)) return
    try {
      const res = await client.get(originalSrc, {responseType: 'blob'})
      const blobUrl = URL.createObjectURL(res.data as Blob)
      blobUrls.add(blobUrl)
      img.src = blobUrl
    } catch {
      img.removeAttribute('src')
      img.alt = img.alt || ''
    }
  }))
}

watch(() => props.html, async () => {
  revokeAll()
  await loadAuthImages()
})

onMounted(loadAuthImages)
onUnmounted(revokeAll)
</script>

<template>
  <div ref="container" class="markdown-content" v-html="html"/>
</template>
