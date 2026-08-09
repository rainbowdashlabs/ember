/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, type ModelRef } from 'vue'

/**
 * Editing one string list inside a question's configuration — the answers of an enumeration, the
 * items of an ordering question, the distractors of a fill-in-the-blank, and so on.
 *
 * The configuration is a loosely-typed object shared by every question type, so each list is
 * addressed by key. Every change replaces both the list and the configuration object rather than
 * mutating them, because the model is what the editor above watches for unsaved changes.
 *
 * @param config the question configuration
 * @param key    the property holding the list
 */
export function useQuestionConfigList(config: ModelRef<Record<string, unknown>>, key: string) {
  const items = computed(() => (config.value[key] as string[] | undefined) ?? [])

  function write(next: string[]) {
    config.value = {...config.value, [key]: next}
  }

  function add() {
    write([...items.value, ''])
  }

  function remove(index: number) {
    const next = [...items.value]
    next.splice(index, 1)
    write(next)
  }

  function update(index: number, value: string) {
    const next = [...items.value]
    next[index] = value
    write(next)
  }

  /**
   * Swaps an entry with its neighbour. Out-of-range moves are ignored rather than clamped, so the
   * first and last entries simply do not move.
   */
  function move(index: number, direction: -1 | 1) {
    const next = [...items.value]
    const target = index + direction
    const current = next[index]
    const other = next[target]
    if (current === undefined || other === undefined) return
    next[index] = other
    next[target] = current
    write(next)
  }

  return {items, add, remove, update, move}
}
