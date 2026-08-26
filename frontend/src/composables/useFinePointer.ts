/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onMounted, onUnmounted, readonly, ref} from 'vue'

const QUERY = '(pointer: fine)'

const finePointer = ref(false)

let watchers = 0
let media: MediaQueryList | null = null

function apply(event: MediaQueryListEvent | MediaQueryList) {
    finePointer.value = event.matches
}

/**
 * Whether the device points with something as precise as a mouse.
 *
 * <p>This is what decides whether dragging is offered at all: a finger cannot pick a row up and put it
 * down again, so a list that can only be dragged cannot be sorted on a phone. It asks about the pointer
 * rather than the width of the window, because a tablet is wide and still has no mouse.
 *
 * <p>It reads false until the component is mounted, so the server and the first client render agree, and
 * whatever is offered only for a mouse appears once the browser has said there is one.
 */
export function useFinePointer() {
    onMounted(() => {
        if (watchers === 0) {
            media = window.matchMedia(QUERY)
            media.addEventListener('change', apply)
        }
        watchers++
        if (media) apply(media)
    })

    onUnmounted(() => {
        watchers--
        if (watchers === 0 && media) {
            media.removeEventListener('change', apply)
            media = null
        }
    })

    return {finePointer: readonly(finePointer)}
}
