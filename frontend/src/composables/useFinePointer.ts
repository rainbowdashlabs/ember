/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useMediaQuery} from '@vueuse/core'
import {computed} from 'vue'
import {useHydrated} from '@/composables/useHydrated'

/**
 * Whether the device points with something as precise as a mouse.
 *
 * <p>This is what decides whether dragging is offered at all: a finger cannot pick a row up and put it
 * down again, so a list that can only be dragged cannot be sorted on a phone. It asks about the pointer
 * rather than the width of the window, because a tablet is wide and still has no mouse.
 *
 * <p>It reads false on the server and while the browser takes up the page the server sent (see
 * {@link useHydrated}), so whatever is offered only for a mouse appears once the browser has said
 * there is one.
 */
export function useFinePointer() {
    const hydrated = useHydrated()
    const fine = useMediaQuery('(pointer: fine)')
    return {finePointer: computed(() => hydrated.value && fine.value)}
}
