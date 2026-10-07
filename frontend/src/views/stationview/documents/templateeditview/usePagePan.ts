/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useEventListener} from '@vueuse/core'

/**
 * Lets a zoomed page be dragged around inside the box that scrolls it, for a mouse or a pen without a
 * wheel that scrolls both ways.
 *
 * <p>A press on the page itself drags it; a press on a field is the field's own, which already keeps
 * the press to itself. Touch is left to the browser, which scrolls under a finger anyway, and the
 * keyboard and the scroll bars reach every part of the page as before.
 *
 * @param box the element that scrolls the page
 * @return whether the page is being dragged right now, for the cursor
 */
export function usePagePan(box: Ref<HTMLElement | null>): Ref<boolean> {
    const panning = ref(false)
    let last: {x: number; y: number} | null = null

    useEventListener(box, 'pointerdown', (event: PointerEvent) => {
        if (event.pointerType === 'touch' || event.button !== 0) return
        const element = box.value
        if (!element) return
        element.setPointerCapture(event.pointerId)
        last = {x: event.clientX, y: event.clientY}
        panning.value = true
        event.preventDefault()
    })

    useEventListener(box, 'pointermove', (event: PointerEvent) => {
        const element = box.value
        if (!last || !element) return
        element.scrollLeft -= event.clientX - last.x
        element.scrollTop -= event.clientY - last.y
        last = {x: event.clientX, y: event.clientY}
    })

    function stop() {
        last = null
        panning.value = false
    }

    useEventListener(box, 'pointerup', stop)
    useEventListener(box, 'pointercancel', stop)
    return panning
}
