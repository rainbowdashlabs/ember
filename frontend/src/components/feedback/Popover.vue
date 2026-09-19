/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {nextTick, ref, useId} from 'vue'
import {useFloatingPanel} from '@/composables/useFloatingPanel'
import {useDismiss} from '@/composables/useDismiss'

/**
 * A panel that hangs off a trigger: a row's menu, a table's column list.
 *
 * <p>The panel is rendered at the body rather than beside its trigger, because a trigger usually
 * sits in something that scrolls, and a panel positioned inside a table with `overflow-x-auto` is
 * cut off at the edge of the table.
 *
 * <p>Standing at the end of the body is also why focus is moved by hand. Tabbing on from the
 * trigger would otherwise walk the whole rest of the page before arriving at the panel that just
 * opened, so the first control in it is focused on open and the trigger gets its focus back on
 * close. The panel is placed before that focus lands, so it is no longer transparent by then.
 *
 * <p>It closes on a press outside, on escape, and when focus is tabbed out of it. A menu also
 * closes once something in it was chosen; a panel of settings, such as a column list, stays open
 * while the reader ticks through it.
 *
 * <p>The trigger comes in through the `trigger` slot, which is handed `toggle`, `open` and the
 * attributes that tie the trigger to its panel.
 */
const props = withDefaults(defineProps<{
  label: string
  role?: 'menu' | 'dialog'
  /** Tells two panels on one page apart. The panel carries it, the trigger is expected to carry it with `-trigger`. */
  testId?: string
  panelClass?: string
}>(), {
  role: 'menu',
  testId: undefined,
  panelClass: 'min-w-44 py-1',
})

const FOCUSABLE = 'button:not([disabled]), a[href], input:not([disabled])'

const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const {panel, style, place} = useFloatingPanel(rootRef, open)
const panelId = useId()

function triggerElement(): HTMLElement | null {
  return rootRef.value?.querySelector<HTMLElement>('button, a[href]') ?? null
}

async function toggle() {
  if (open.value) {
    close()
    return
  }
  open.value = true
  await nextTick()
  place()
  const first = panel.value?.querySelector<HTMLElement>(FOCUSABLE)
  const landing = first ?? panel.value
  landing?.focus()
}

/**
 * Closes the panel.
 *
 * @param restoreFocus whether focus goes back to the trigger, which it should whenever the reader
 *                     left the panel by choosing something or by pressing escape, and should not
 *                     when they have already put their focus somewhere else
 */
function close(restoreFocus = true) {
  if (!open.value) return
  open.value = false
  if (restoreFocus) triggerElement()?.focus()
}

useDismiss(open, () => [rootRef.value, panel.value], by => close(by === 'escape'))

function onFocusOut(event: FocusEvent) {
  const next = event.relatedTarget as Node | null
  if (!next || rootRef.value?.contains(next) || panel.value?.contains(next)) return
  close(false)
}

function onPanelClick() {
  if (props.role === 'menu') close()
}
</script>

<template>
  <div ref="rootRef">
    <slot
        :open="open"
        :toggle="toggle"
        :trigger-attrs="{'aria-controls': panelId, 'aria-expanded': open, 'aria-haspopup': role}"
        name="trigger"
    />
    <Teleport to="body">
      <div
          v-if="open"
          :id="panelId"
          ref="panel"
          :aria-label="label"
          :class="panelClass"
          :data-testid="testId"
          :role="role"
          :style="style"
          class="max-h-[60vh] overflow-y-auto rounded-theme border border-(--border) bg-(--bg) shadow-lg z-50 text-left"
          tabindex="-1"
          @click="onPanelClick"
          @focusout="onFocusOut"
      >
        <slot/>
      </div>
    </Teleport>
  </div>
</template>
