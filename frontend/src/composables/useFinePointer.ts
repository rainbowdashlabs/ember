/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useMediaQuery, useMounted} from '@vueuse/core'
import {computed} from 'vue'

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
    const mounted = useMounted()
    const fine = useMediaQuery('(pointer: fine)')
    return {finePointer: computed(() => mounted.value && fine.value)}
}
