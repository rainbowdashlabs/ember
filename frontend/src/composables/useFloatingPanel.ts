/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, nextTick, onBeforeUnmount, ref, watch, type CSSProperties, type Ref} from 'vue'

/** How far a panel stands off the thing it belongs to, and off the edge of the window. */
const GAP = 4

/**
 * Where a panel that hangs off a button goes, once it has left the layout to get there.
 *
 * <p>A panel positioned inside its own corner of the page is at the mercy of every ancestor: one
 * with `overflow` clips it, and the reader is left scrolling a table sideways to read a menu. The
 * way out is to render it at the body and place it by hand, which is what this works out: hanging
 * under the button and aligned to its right edge, flipped above when there is no room below, and
 * never past the edge of the window.
 *
 * <p>The placement is redone while the panel is open, because everything it was measured against
 * can move: the page scrolls, a pane scrolls, the window is resized.
 *
 * <p>A panel has to be in the document before it can be measured, so for one frame it stands at
 * the top left of the window. It is therefore held transparent until it has been placed, which is
 * the difference between a menu that appears where it belongs and one that flashes in the corner
 * first. Transparent rather than hidden, because a hidden element cannot take focus, and the menu
 * hands focus to its first entry the moment it opens.
 *
 * @param anchor the element the panel belongs to, whose rectangle it is placed against
 * @param open   whether the panel is showing
 */
export function useFloatingPanel(anchor: Ref<HTMLElement | null>, open: Ref<boolean>) {
    const panel = ref<HTMLElement | null>(null)
    const top = ref(0)
    const left = ref(0)
    const placed = ref(false)

    function place() {
        const trigger = anchor.value
        const box = panel.value
        if (!trigger || !box) return
        const rect = trigger.getBoundingClientRect()
        const width = box.offsetWidth
        const height = box.offsetHeight

        const below = window.innerHeight - rect.bottom - GAP
        const above = rect.top - GAP
        const opensUpwards = height > below && above > below
        top.value = opensUpwards ? Math.max(GAP, rect.top - GAP - height) : rect.bottom + GAP

        const rightAligned = rect.right - width
        const furthestLeft = Math.max(GAP, window.innerWidth - width - GAP)
        left.value = Math.min(Math.max(GAP, rightAligned), furthestLeft)
        placed.value = true
    }

    function watchTheView() {
        window.addEventListener('scroll', place, true)
        window.addEventListener('resize', place)
    }

    function stopWatching() {
        window.removeEventListener('scroll', place, true)
        window.removeEventListener('resize', place)
    }

    watch(open, async (showing) => {
        if (!showing) {
            placed.value = false
            stopWatching()
            return
        }
        await nextTick()
        place()
        watchTheView()
    })

    onBeforeUnmount(stopWatching)

    const style = computed<CSSProperties>(() => ({
        position: 'fixed',
        top: `${top.value}px`,
        left: `${left.value}px`,
        opacity: placed.value ? 1 : 0,
    }))

    return {panel, style, place}
}
