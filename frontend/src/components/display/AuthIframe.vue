/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch, onUnmounted} from 'vue'
import client from '@/api/client'

const props = defineProps<{
  src: string
  title?: string
}>()

const blobUrl = ref<string | null>(null)
const loading = ref(false)
const error = ref(false)

async function load() {
  if (!props.src) {
    cleanup()
    return
  }
  loading.value = true
  error.value = false
  try {
    const res = await client.get(props.src, {responseType: 'blob'})
    cleanup()
    blobUrl.value = URL.createObjectURL(res.data as Blob)
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

function cleanup() {
  if (blobUrl.value) {
    URL.revokeObjectURL(blobUrl.value)
    blobUrl.value = null
  }
}

watch(() => props.src, load, {immediate: true})
onUnmounted(cleanup)
</script>

<template>
  <iframe v-if="blobUrl" :src="blobUrl" :title="title ?? ''" v-bind="$attrs"/>
  <slot v-else-if="error" name="error"/>
  <slot v-else-if="loading" name="loading"/>
</template>
