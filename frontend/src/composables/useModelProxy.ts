/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type WritableComputedRef} from 'vue'

/**
 * Builds a writable `computed` that mirrors a one-way `props.X` value and
 * forwards writes through `emit('update:X', v)`. Replaces the repeated
 * "computed-with-get-and-set" boilerplate that every modal and form section
 * writes when bridging a `v-model` between parent and child. The emit
 * parameter only requires the matching `update:K` overload — components that
 * declare additional events (e.g. `save`) stay assignable because Vue's
 * `defineEmits` return value is the intersection of every overload.
 *
 * Usage:
 * ```ts
 * const openProxy = useModelProxy(() => props.modelValue, emit, 'modelValue')
 * const nameProxy = useModelProxy(() => props.name, emit, 'name')
 * ```
 */
export function useModelProxy<K extends string, T>(
    getter: () => T,
    emit: (event: `update:${K}`, value: T) => void,
    key: K,
): WritableComputedRef<T> {
    return computed({
        get: getter,
        set: (v: T) => emit(`update:${key}`, v),
    })
}
