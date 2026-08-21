/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { onBeforeUnmount, onMounted } from 'vue'
import { useQuickSearch, type QuickSearchScope } from '@/composables/useQuickSearch'

/**
 * Binds Ctrl+K - Cmd+K on macOS - to the quick-search palette for as long as the calling shell is
 * mounted, so the shortcut follows whichever shell the user is in and cannot outlive it.
 *
 * The shortcut toggles rather than opens, because the browser's own find dialog is not available
 * once the default is suppressed and a user who opened the palette by accident needs the same key
 * to get out.
 *
 * @param scope which palette to open - the station one or the admin one
 */
export function useQuickSearchShortcut(scope: QuickSearchScope) {
  const {open, close, isOpen} = useQuickSearch()

  function onKeydown(event: KeyboardEvent) {
    if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'k') return
    event.preventDefault()
    if (isOpen.value) close()
    else open(scope)
  }

  onMounted(() => window.addEventListener('keydown', onKeydown))
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

  /** Opens the palette this shell owns, for the header's search button. */
  function openScoped() {
    open(scope)
  }

  return {open: openScoped, close, isOpen}
}
