/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { useBreakpoint } from '@/composables/useBreakpoint'

/**
 * The overflow menu attached to a page-editor row or cell.
 *
 * The trigger is revealed on hover on a pointer device and shown permanently on touch, where
 * there is no hover to reveal it with. The menu closes on any click outside itself, which is
 * bound on the document rather than a backdrop so the click that closes it still reaches whatever
 * it landed on.
 */
export function useEditorActionsMenu() {
  const { isMobile } = useBreakpoint()

  const open = ref(false)
  const rootRef = ref<HTMLElement | null>(null)

  const triggerVisibility = computed(() =>
    isMobile.value ? 'opacity-100' : 'opacity-0 group-hover:opacity-100 focus-within:opacity-100')

  function close() {
    open.value = false
  }

  function toggle(event: MouseEvent) {
    event.stopPropagation()
    open.value = !open.value
  }

  function onDocumentClick(event: MouseEvent) {
    if (rootRef.value && !rootRef.value.contains(event.target as Node)) close()
  }

  if (typeof document !== 'undefined') {
    document.addEventListener('click', onDocumentClick)
    onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))
  }

  return {open, rootRef, triggerVisibility, toggle, close}
}
