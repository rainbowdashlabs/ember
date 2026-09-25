/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type WritableComputedRef} from 'vue'
import {useRoute, useRouter} from 'vue-router'

/**
 * One query parameter of the current address, read and written as a ref.
 *
 * <p>What a reader narrowed a screen to belongs in the address rather than in the component: a list
 * they can copy the address of is a list they can send to somebody else and come back to, and the
 * back button takes them to what they were looking at instead of to the screen's starting state.
 *
 * <p>The parameter is dropped from the address whenever it holds the fallback, so a screen nobody
 * has touched keeps a plain address. The value is replaced rather than pushed: narrowing a list is
 * not a place of its own, and a history entry per keystroke would bury the page the reader came
 * from.
 *
 * @param key      the parameter's name in the address
 * @param fallback what the ref reads while the parameter is absent
 */
export function useRouteQueryRef(key: string, fallback = ''): WritableComputedRef<string> {
    const route = useRoute()
    const router = useRouter()

    return computed({
        get() {
            const raw = route.query[key]
            const value = Array.isArray(raw) ? raw[0] : raw
            return typeof value === 'string' && value !== '' ? value : fallback
        },
        set(value) {
            const query = {...route.query}
            if (value === '' || value === fallback) delete query[key]
            else query[key] = value
            router.replace({query})
        },
    })
}
