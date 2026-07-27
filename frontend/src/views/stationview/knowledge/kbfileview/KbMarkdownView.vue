/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useAuthImages} from '@/composables/useAuthImage'
import {isKbImageSrc} from '@/util/normalizeAuthSrc'

const props = defineProps<{
  html: string
}>()

const container = ref<HTMLDivElement | null>(null)
const {load, srcFor, revokeAll} = useAuthImages<string>()

async function loadAuthImages() {
  if (!container.value) return
  const images = Array.from(container.value.querySelectorAll<HTMLImageElement>('img'))
  const targets = images
      .map(img => ({img, src: img.getAttribute('src') ?? ''}))
      .filter(({src}) => isKbImageSrc(src))
  const uniqueSrcs = [...new Set(targets.map(target => target.src))]
  await Promise.all(uniqueSrcs.map(src => load(src, src)))
  for (const {img, src} of targets) {
    const blobUrl = srcFor(src)
    if (blobUrl) {
      img.src = blobUrl
    } else {
      img.removeAttribute('src')
    }
  }
}

watch(() => props.html, async () => {
  revokeAll()
  await loadAuthImages()
})

onMounted(loadAuthImages)
</script>

<template>
  <div ref="container" class="markdown-content" v-html="html"/>
</template>
