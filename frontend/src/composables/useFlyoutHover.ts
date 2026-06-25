/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onBeforeUnmount, ref} from 'vue'

/**
 * Delayed open/close state for hover-driven flyouts. The anchor element and the
 * flyout itself both call {@link enter} on `mouseenter` / `focusin` and
 * {@link leave} on `mouseleave` / `focusout`. The shared close-timer absorbs the
 * brief cursor gap between the two elements, giving a "hover corridor" feel
 * without explicit polygon math.
 */
export function useFlyoutHover(openDelay = 150, closeDelay = 200) {
    const open = ref(false)
    let openTimer: number | undefined
    let closeTimer: number | undefined

    function clearTimers() {
        if (openTimer !== undefined) {
            window.clearTimeout(openTimer)
            openTimer = undefined
        }
        if (closeTimer !== undefined) {
            window.clearTimeout(closeTimer)
            closeTimer = undefined
        }
    }

    function enter() {
        if (closeTimer !== undefined) {
            window.clearTimeout(closeTimer)
            closeTimer = undefined
        }
        if (open.value) return
        if (openTimer !== undefined) return
        openTimer = window.setTimeout(() => {
            openTimer = undefined
            open.value = true
        }, openDelay)
    }

    function leave() {
        if (openTimer !== undefined) {
            window.clearTimeout(openTimer)
            openTimer = undefined
        }
        if (!open.value) return
        if (closeTimer !== undefined) return
        closeTimer = window.setTimeout(() => {
            closeTimer = undefined
            open.value = false
        }, closeDelay)
    }

    function force(value: boolean) {
        clearTimers()
        open.value = value
    }

    onBeforeUnmount(clearTimers)

    return {open, enter, leave, force}
}
