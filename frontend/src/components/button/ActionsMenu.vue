/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {nextTick, onBeforeUnmount, onMounted, ref, useId} from 'vue'
import IconButton from './IconButton.vue'
import {useFloatingPanel} from '@/composables/useFloatingPanel'

/**
 * The menu of what can be done to one row, or to the page a toolbar belongs to.
 *
 * <p>Its list is rendered at the body rather than beside the button, because a row usually sits in
 * something that scrolls: a list positioned inside a table with `overflow-x-auto` is cut off at the
 * edge of the table, and the reader has to scroll sideways to read their own menu.
 *
 * <p>Standing at the end of the body is also why focus is moved by hand. Tabbing on from the
 * trigger would otherwise walk the whole rest of the page before arriving at the menu that just
 * opened, so the first entry is focused on open and the trigger gets its focus back on close.
 *
 * <p>Two conventions for what goes in the slot: the destructive entry comes last and is coloured,
 * the way the row menu of the item table does it, because a full width row directly under a
 * harmless one reads as harmless otherwise. And the action the reader came for is not in here at
 * all - it stays a button of its own beside the menu.
 */
withDefaults(defineProps<{
  label: string
  icon?: string[]
  /** Tells two menus on one page apart. The panel carries it, the trigger carries it with `-trigger`. */
  testId?: string
}>(), {
  icon: () => ['fas', 'ellipsis-vertical'],
  testId: 'actions-menu',
})

const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const {panel, style, place} = useFloatingPanel(rootRef, open)
const panelId = useId()

function triggerButton(): HTMLElement | null {
  return rootRef.value?.querySelector('button') ?? null
}

async function toggle() {
  if (open.value) {
    close()
    return
  }
  open.value = true
  // Placed here rather than left to the watcher, so the panel is where it belongs and no longer
  // transparent by the time its first entry is focused.
  await nextTick()
  place()
  const first = panel.value?.querySelector<HTMLElement>('button:not([disabled]), a[href]')
  const landing = first ?? panel.value
  landing?.focus()
}

/**
 * Closes the menu.
 *
 * @param restoreFocus whether focus goes back to the trigger, which it should whenever the reader
 *                     left the menu by choosing something or by pressing escape, and should not
 *                     when they have already put their focus somewhere else
 */
function close(restoreFocus = true) {
  if (!open.value) return
  open.value = false
  if (restoreFocus) triggerButton()?.focus()
}

function onDocClick(e: MouseEvent) {
  if (!open.value) return
  const target = e.target as Node
  if (rootRef.value?.contains(target) || panel.value?.contains(target)) return
  close(false)
}

function onEscape(e: KeyboardEvent) {
  if (e.key === 'Escape') close()
}

/** Tabbing out of the panel leaves the menu behind, so it closes rather than following along. */
function onFocusOut(e: FocusEvent) {
  const next = e.relatedTarget as Node | null
  if (!next || rootRef.value?.contains(next) || panel.value?.contains(next)) return
  close(false)
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
        :aria-controls="panelId"
        :aria-expanded="open"
        :data-testid="`${testId}-trigger`"
        aria-haspopup="menu"
        class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
        @click.stop="toggle"
    />
    <Teleport to="body">
      <div
          v-if="open"
          :id="panelId"
          ref="panel"
          :aria-label="label"
          :data-testid="testId"
          :style="style"
          class="min-w-44 max-h-[60vh] overflow-y-auto rounded-theme border border-(--border) bg-(--bg) shadow-lg py-1 z-50 text-left"
          role="menu"
          tabindex="-1"
          @click="close()"
          @focusout="onFocusOut"
      >
        <slot/>
      </div>
    </Teleport>
  </div>
</template>
