/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import IconButton from './IconButton.vue'

withDefaults(defineProps<{
  label: string
  icon?: string[]
}>(), {
  icon: () => ['fas', 'ellipsis-vertical'],
})

const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)

function onDocClick(e: MouseEvent) {
  if (open.value && rootRef.value && !rootRef.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <div ref="rootRef" class="relative inline-block">
    <IconButton :icon="icon" :label="label" class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent" @click.stop="open = !open"/>
    <div v-if="open" class="absolute right-0 top-full mt-1 min-w-44 rounded-theme border border-(--border) bg-(--bg) shadow-lg py-1 z-20 text-left" @click="open = false">
      <slot/>
    </div>
  </div>
</template>
