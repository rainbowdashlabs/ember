/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {nextTick, onBeforeUnmount, ref, watch} from 'vue'
import {provideSidebarFlyoutContext} from '@/composables/useSidebarFlyoutContext'

const props = defineProps<{
  anchor: HTMLElement | null
  open: boolean
  label?: string
  icon?: string[]
  badge?: number
  to?: string
}>()

const emit = defineEmits<{
  enter: []
  leave: []
  close: []
  'header-click': []
}>()

provideSidebarFlyoutContext()

const panel = ref<HTMLDivElement | null>(null)
const left = ref(0)
const top = ref(0)
const visible = ref(false)

function reposition() {
  const anchorEl = props.anchor
  const panelEl = panel.value
  if (!anchorEl || !panelEl) return
  const anchorRect = anchorEl.getBoundingClientRect()
  const panelRect = panelEl.getBoundingClientRect()
  const margin = 8
  const vw = window.innerWidth
  const vh = window.innerHeight

  let nextLeft = anchorRect.right + margin
  if (nextLeft + panelRect.width > vw - margin) {
    nextLeft = anchorRect.left - panelRect.width - margin
  }
  if (nextLeft < margin) nextLeft = margin

  let nextTop = anchorRect.top
  if (nextTop + panelRect.height > vh - margin) {
    nextTop = Math.max(margin, vh - panelRect.height - margin)
  }
  if (nextTop < margin) nextTop = margin

  left.value = nextLeft
  top.value = nextTop
}

async function showAndPlace() {
  visible.value = true
  await nextTick()
  reposition()
}

watch(() => props.open, (value) => {
  if (value) {
    showAndPlace()
  } else {
    visible.value = false
  }
}, {immediate: true})

watch(() => props.anchor, () => {
  if (props.open) showAndPlace()
})

function onWindowChange() {
  if (props.open) reposition()
}

function onDocumentMouseDown(event: MouseEvent) {
  if (!props.open) return
  const target = event.target as Node | null
  if (!target) return
  if (panel.value?.contains(target)) return
  if (props.anchor?.contains(target)) return
  emit('close')
}

function onKeyDown(event: KeyboardEvent) {
  if (!props.open) return
  if (event.key === 'Escape') {
    event.stopPropagation()
    emit('close')
  }
}

watch(() => props.open, (value) => {
  if (value) {
    window.addEventListener('resize', onWindowChange)
    window.addEventListener('scroll', onWindowChange, true)
    document.addEventListener('mousedown', onDocumentMouseDown)
    document.addEventListener('keydown', onKeyDown)
  } else {
    window.removeEventListener('resize', onWindowChange)
    window.removeEventListener('scroll', onWindowChange, true)
    document.removeEventListener('mousedown', onDocumentMouseDown)
    document.removeEventListener('keydown', onKeyDown)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowChange)
  window.removeEventListener('scroll', onWindowChange, true)
  document.removeEventListener('mousedown', onDocumentMouseDown)
  document.removeEventListener('keydown', onKeyDown)
})

function onHeaderClick() {
  emit('header-click')
}
</script>

<template>
  <Teleport to="body">
    <div
        v-if="visible"
        ref="panel"
        role="menu"
        :style="{left: `${left}px`, top: `${top}px`}"
        class="sidebar-flyout fixed z-50 min-w-56 max-w-72 rounded-theme border bg-bg-light-accent dark:bg-bg-dark-accent shadow-xl py-1.5"
        @mouseenter="emit('enter')"
        @mouseleave="emit('leave')"
        @focusin="emit('enter')"
        @focusout="emit('leave')"
    >
      <component
          :is="to ? 'router-link' : 'div'"
          v-if="label"
          :to="to"
          :class="to ? 'cursor-pointer hover:bg-primary/5' : ''"
          class="flex items-center gap-3 px-3 py-2 mb-1 border-b border-bg-light dark:border-bg-dark text-sm font-semibold no-underline text-[var(--text)]"
          @click="onHeaderClick"
      >
        <font-awesome-icon v-if="icon" :icon="icon" class="w-4 shrink-0"/>
        <span class="flex-1 truncate">{{ label }}</span>
        <span v-if="badge && badge > 0"
              class="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-bold bg-error text-error-text">{{
            badge
          }}</span>
      </component>
      <div class="flex flex-col gap-0.5 px-1">
        <slot/>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.sidebar-flyout {
  border-color: var(--bg-light);
}

:global(.dark) .sidebar-flyout {
  border-color: var(--bg-dark);
}
</style>
