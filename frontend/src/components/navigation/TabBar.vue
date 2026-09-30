/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, type ComponentPublicInstance} from 'vue'
import BareButton from '@/components/button/BareButton.vue'

/**
 * One entry of a tab list: the value it selects and what it says.
 *
 * <p>The list is a `tablist` of `tab`s with a roving tabindex, and the arrow keys, Home and End
 * select as they move. The rule under it belongs to the outer element and the scrolling to the
 * inner one, which keeps a horizontal scroller from growing a vertical scrollbar: the active tab's
 * border, pulled down a pixel onto the rule, is enough to make Chrome show one otherwise.
 */
interface Tab {
  key: string
  label: string
}

const modelValue = defineModel<string>({required: true})

const props = defineProps<{
  tabs: Tab[]
}>()

const tabElements: HTMLButtonElement[] = []

/** Keeps the element of the tab at `index`, so the arrows can move the focus onto it. */
function rememberTab(index: number, tab: Element | ComponentPublicInstance | null) {
  const element = tab && '$el' in tab ? tab.$el : tab
  if (element instanceof HTMLButtonElement) tabElements[index] = element
}

/**
 * The tab the keyboard reaches with Tab: the selected one, or the first while none is.
 *
 * <p>Only that one sits in the tab order; the arrow keys move between the rest, which is the
 * roving tabindex a tab list is expected to have.
 */
const focusableIndex = computed(() => Math.max(0, props.tabs.findIndex(tab => tab.key === modelValue.value)))

/**
 * Where a key sends the focus from the tab at `index`, or nothing for a key the tab list leaves
 * alone. The arrows wrap around at either end.
 */
function targetOf(key: string, index: number): number | null {
  const count = props.tabs.length
  if (key === 'ArrowRight') return (index + 1) % count
  if (key === 'ArrowLeft') return (index - 1 + count) % count
  if (key === 'Home') return 0
  if (key === 'End') return count - 1
  return null
}

/** Selects the tab at `index` and moves the focus onto it, as the arrows activate as they go. */
function activate(index: number) {
  const tab = props.tabs[index]
  if (!tab) return
  modelValue.value = tab.key
  tabElements[index]?.focus()
}

function onKeydown(event: KeyboardEvent, index: number) {
  const target = targetOf(event.key, index)
  if (target === null) return
  event.preventDefault()
  activate(target)
}
</script>

<template>
  <div class="border-b border-bg-light-accent dark:border-bg-dark-accent">
    <div class="-mb-px flex gap-2 overflow-x-auto overflow-y-hidden" role="tablist">
      <BareButton
          v-for="(tab, index) in tabs"
          :key="tab.key"
          :ref="element => rememberTab(index, element)"
          :aria-selected="modelValue === tab.key"
          :class="modelValue === tab.key ? 'border-primary text-primary' : 'border-transparent text-(--text-muted) hover:text-(--text)'"
          :tabindex="index === focusableIndex ? 0 : -1"
          class="shrink-0 whitespace-nowrap px-4 py-2 text-sm font-medium transition-colors border-b-2"
          role="tab"
          @click="activate(index)"
          @keydown="onKeydown($event, index)"
      >
        {{ tab.label }}
      </BareButton>
    </div>
  </div>
</template>
