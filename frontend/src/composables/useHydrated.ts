/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useMounted} from '@vueuse/core'
import {computed, type Ref} from 'vue'

/**
 * Whether a component may show what only the browser knows, such as the width of the screen or the
 * kind of pointer.
 *
 * <p>A component set up while the browser takes up the page the server sent reads false until it is
 * mounted. The server knows none of those facts, and a first render in the browser that already
 * knew them would disagree with its markup: Vue repairs a structure that disagrees with a console
 * error and keeps a class that disagrees for good. A component set up any later has nothing to agree
 * with and reads true from the start, so a page rendered in the browser alone never draws twice.
 */
export function useHydrated(): Readonly<Ref<boolean>> {
    const hydrating = tryUseNuxtApp()?.isHydrating ?? false
    const mounted = useMounted()
    return computed(() => !hydrating || mounted.value)
}
