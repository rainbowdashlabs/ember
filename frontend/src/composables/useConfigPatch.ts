/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Ref, unref} from 'vue'

/**
 * Source of the current configuration object - either the raw `props.config`
 * value (when used inline as `useConfigPatch(() => props.config, emit)`) or a
 * Vue ref/computed wrapping it. Both shapes are unwrapped per call so the
 * helper always emits a merge against the freshest snapshot.
 */
export type ConfigSource<T extends Record<string, unknown>> = T | Ref<T> | (() => T)

/**
 * Emitter shape produced by `defineEmits<{'update:config': [value: T], ...}>()`.
 * Only the `update:config` overload is required - components that emit
 * additional events keep their own typed emitter and remain assignable
 * because intersection function types narrow to either individual overload.
 */
export type ConfigEmit<T extends Record<string, unknown>> = (event: 'update:config', value: T) => void

function resolve<T extends Record<string, unknown>>(source: ConfigSource<T>): T {
    if (typeof source === 'function') return (source as () => T)()
    return unref(source as T | Ref<T>)
}

/**
 * Returns a `patch(partial)` helper for cell-editor components that own a
 * `config` prop and emit `update:config`. Each call merges the partial into
 * the current config snapshot and emits the result, replacing the repeated
 * inline helper used across the page editor cells.
 */
export function useConfigPatch<T extends Record<string, unknown>>(
    source: ConfigSource<T>,
    emit: ConfigEmit<T>,
): (partial: Partial<T>) => void {
    return function patch(partial: Partial<T>) {
        emit('update:config', {...resolve(source), ...partial})
    }
}
