/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onBeforeUnmount} from 'vue'

/** How long a finger has to rest on something before it counts as a long press, in milliseconds. */
export const LONG_PRESS_MS = 500

/** How far a resting finger may wander before the touch counts as a scroll, in pixels. */
const WANDER_PX = 10

/**
 * A long press with a finger, which is how a phone reaches what a mouse reaches by hovering.
 * Moving the finger or lifting it early is an ordinary touch or scroll and does nothing.
 *
 * @param pressed called once the finger has rested long enough
 * @returns the pointer handlers, named by their events for `v-on`
 */
export function useLongPress(pressed: () => void) {
    let timer: ReturnType<typeof setTimeout> | null = null
    let startX = 0
    let startY = 0

    function cancel() {
        if (timer) clearTimeout(timer)
        timer = null
    }

    function onPointerdown(event: PointerEvent) {
        if (event.pointerType !== 'touch') return
        cancel()
        startX = event.clientX
        startY = event.clientY
        timer = setTimeout(() => {
            timer = null
            pressed()
        }, LONG_PRESS_MS)
    }

    function onPointermove(event: PointerEvent) {
        if (!timer) return
        if (Math.abs(event.clientX - startX) > WANDER_PX || Math.abs(event.clientY - startY) > WANDER_PX) cancel()
    }

    onBeforeUnmount(cancel)

    return {pointerdown: onPointerdown, pointermove: onPointermove, pointerup: cancel, pointercancel: cancel}
}
