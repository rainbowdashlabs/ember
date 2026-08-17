/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'

const DEFAULT_DELAY_MS = 300

/**
 * A search box backed by a server query, debounced so typing does not send a request per keystroke.
 *
 * Clearing the box empties the results immediately rather than after the delay, so the list never
 * shows results for a query the user has already deleted. A failed search also empties them -
 * showing the previous query's results under a new query would be worse than showing none.
 *
 * @param fetch   runs the search for a non-empty, trimmed query
 * @param delayMs how long to wait after the last keystroke
 */
export function useDebouncedSearch<T>(
  fetch: (query: string) => Promise<T[]>,
  delayMs = DEFAULT_DELAY_MS,
) {
  const query = ref('')
  const results = ref<T[]>([]) as Ref<T[]>
  const searching = ref(false)
  const isSearching = computed(() => query.value.trim().length > 0)

  let timeout: ReturnType<typeof setTimeout> | null = null

  async function run() {
    const trimmed = query.value.trim()
    if (!trimmed) {
      results.value = []
      return
    }
    searching.value = true
    try {
      results.value = await fetch(trimmed)
    } catch {
      results.value = []
    } finally {
      searching.value = false
    }
  }

  function onInput() {
    if (timeout) clearTimeout(timeout)
    if (!query.value.trim()) {
      results.value = []
      return
    }
    timeout = setTimeout(run, delayMs)
  }

  return {query, results, searching, isSearching, onInput, run}
}
