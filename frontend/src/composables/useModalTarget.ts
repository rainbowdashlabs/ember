/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'

/**
 * Tracks a modal's open/closed state together with the entity it is acting on.
 *
 * Replaces the common pair of refs `showXModal` / `xTarget` and the
 * `openX(t) { target.value = t; isOpen.value = true }` helper that
 * accompanied them across many view components.
 *
 * `reset` runs every time the modal is opened (after the new target has been
 * assigned) and again on close - use it to clear input fields bound to the
 * modal so reopens always start from a clean slate.
 */
export function useModalTarget<T>(reset?: () => void) {
    const isOpen = ref(false) as Ref<boolean>
    const target = ref<T | null>(null) as Ref<T | null>

    function open(value: T | null = null) {
        target.value = value
        if (reset) reset()
        isOpen.value = true
    }

    function close() {
        isOpen.value = false
        if (reset) reset()
    }

    return {isOpen, target, open, close}
}
