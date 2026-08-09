/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'

/**
 * Exposes a reactive state object as the prop bag and the matching `update:`
 * handler map that a `v-bind` / `v-on` bound editor component speaks, so every
 * field is declared exactly once.
 */
export function modelBindings<T extends object>(state: T) {
  const props = computed(() => ({...state}))

  const handlers: Record<string, (value: unknown) => void> = {}
  for (const key of Object.keys(state)) {
    handlers[`update:${key}`] = (value: unknown) => {
      (state as Record<string, unknown>)[key] = value
    }
  }

  return {props, handlers}
}
