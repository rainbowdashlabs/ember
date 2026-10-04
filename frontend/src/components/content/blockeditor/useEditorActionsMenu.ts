/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref } from 'vue'
import { useBreakpoint } from '@/composables/useBreakpoint'

/**
 * The overflow menu attached to a page-editor row or cell, or to a question or page of a form.
 *
 * The trigger is revealed on hover on a pointer device and shown permanently on touch, where
 * there is no hover to reveal it with. While the menu is open the trigger stays visible too, since
 * the menu is rendered at the end of the page and the pointer leaves the row to reach it. The
 * menu itself is a popover bound to `open`, which closes on a press outside, on escape and once
 * an entry was chosen.
 */
export function useEditorActionsMenu() {
  const { isMobile } = useBreakpoint()

  const open = ref(false)

  const triggerVisibility = computed(() =>
    isMobile.value || open.value ? 'opacity-100' : 'opacity-0 group-hover:opacity-100 focus-within:opacity-100')

  function close() {
    open.value = false
  }

  return {open, triggerVisibility, close}
}
