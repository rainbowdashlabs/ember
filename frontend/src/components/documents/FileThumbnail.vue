/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, onUnmounted, ref, watch} from 'vue'
import client from '@/api/client'
import {canHavePicture, fileKindIcon} from '@/util/fileKind'

/**
 * The picture of a file, or the kind of file it is where there is none.
 *
 * <p>The bytes come from an endpoint that wants a token, so the picture cannot simply be pointed at
 * and is fetched into an object URL the way every other guarded image here is. A file whose kind
 * carries no picture is never asked for one: the server would refuse, and a refusal per tile is a
 * row of failed requests to say what the type alone already said.
 *
 * <p>A refusal is not a fault either. A picture is made when a file is stored, so one stored before
 * this existed has none until it is stored again, and the icon is the honest answer meanwhile.
 */
const props = defineProps<{
  /** Where the picture comes from, already carrying the width this tile wants. */
  url: string
  mimeType?: string | null
  alt?: string
  /** The size classes of the tile, so a list and a row can ask for different ones. */
  size?: string
}>()

const root = ref<HTMLElement | null>(null)
const objectUrl = ref<string | null>(null)
const wanted = ref(false)

/**
 * Which request the picture on screen belongs to.
 *
 * <p>A tile is unmounted while its picture is still on its way, and two of them arrive out of the
 * order they were asked for. Both end with an address nobody revokes and, in the second case, the
 * wrong picture: a grid that is filtered or paged unmounts many at once, so the tab keeps every one
 * of them until it is closed.
 */
let current = 0

function revoke() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = null
  }
}

async function load() {
  const mine = ++current
  revoke()
  if (!canHavePicture(props.mimeType) || !props.url) return
  try {
    const res = await client.get(props.url, {responseType: 'blob'})
    const url = URL.createObjectURL(res.data as Blob)
    if (mine !== current) {
      URL.revokeObjectURL(url)
      return
    }
    objectUrl.value = url
  } catch {
    if (mine === current) objectUrl.value = null
  }
}

/**
 * Nothing is fetched until the tile is somewhere near the screen.
 *
 * <p>A browser skips an image it cannot see; a fetch does not, so a library page of two hundred files
 * asked for two hundred pictures at once the moment it opened. Where the browser cannot tell us what
 * is on screen, everything is wanted at once, which is what used to happen anyway.
 */
let observer: IntersectionObserver | null = null

onMounted(() => {
  if (typeof IntersectionObserver === 'undefined' || !root.value) {
    wanted.value = true
    return
  }
  observer = new IntersectionObserver(entries => {
    if (!entries.some(entry => entry.isIntersecting)) return
    wanted.value = true
    observer?.disconnect()
    observer = null
  }, {rootMargin: '200px'})
  observer.observe(root.value)
})

watch(() => [props.url, props.mimeType, wanted.value], () => {
  if (wanted.value) load()
}, {immediate: true})

onUnmounted(() => {
  current++
  observer?.disconnect()
  revoke()
})
</script>

<template>
  <div
      ref="root"
      :class="size ?? 'h-12 w-12'"
      class="shrink-0 flex items-center justify-center overflow-hidden rounded-theme bg-bg-light-accent/40 dark:bg-bg-dark-accent/40"
  >
    <img v-if="objectUrl" :src="objectUrl" :alt="alt ?? ''" class="h-full w-full object-cover"/>
    <font-awesome-icon v-else :icon="fileKindIcon(mimeType)" class="text-(--text-muted)"/>
  </div>
</template>
