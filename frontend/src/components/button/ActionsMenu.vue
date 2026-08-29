/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import IconButton from './IconButton.vue'
import {useFloatingPanel} from '@/composables/useFloatingPanel'

/**
 * The menu of what can be done to one row.
 *
 * <p>Its list is rendered at the body rather than beside the button, because a row usually sits in
 * something that scrolls: a list positioned inside a table with `overflow-x-auto` is cut off at the
 * edge of the table, and the reader has to scroll sideways to read their own menu.
 */
withDefaults(defineProps<{
  label: string
  icon?: string[]
}>(), {
  icon: () => ['fas', 'ellipsis-vertical'],
})

const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const {panel, style} = useFloatingPanel(rootRef, open)

function onDocClick(e: MouseEvent) {
  if (!open.value) return
  const target = e.target as Node
  if (rootRef.value?.contains(target) || panel.value?.contains(target)) return
  open.value = false
}

function onEscape(e: KeyboardEvent) {
  if (e.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onEscape)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <div ref="rootRef" class="inline-block">
    <IconButton
        :icon="icon"
        :label="label"
        class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
        @click.stop="open = !open"
    />
    <Teleport to="body">
      <div
          v-if="open"
          ref="panel"
          :style="style"
          class="min-w-44 rounded-theme border border-(--border) bg-(--bg) shadow-lg py-1 z-50 text-left"
          data-testid="actions-menu"
          @click="open = false"
      >
        <slot/>
      </div>
    </Teleport>
  </div>
</template>
