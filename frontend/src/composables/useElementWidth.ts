/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onBeforeUnmount, onMounted, readonly, ref, type Ref} from 'vue'

function rootFontSizePx(): number {
    return parseFloat(getComputedStyle(document.documentElement).fontSize) || 16
}

/**
 * The width of an element in rem, kept current while it is mounted.
 *
 * <p>For layouts that have to follow the room they are given rather than the size of the window, such
 * as a table in one half of a two column page. In rem so it compares directly with the widths the
 * Tailwind classes are written in. `null` until the first measurement, which is also what the server
 * renders, so a caller falls back to its window based choice until then.
 *
 * @param target the element to measure
 */
export function useElementWidth(target: Ref<HTMLElement | null>): Readonly<Ref<number | null>> {
    const width = ref<number | null>(null)
    let observer: ResizeObserver | null = null

    onMounted(() => {
        if (!target.value || typeof ResizeObserver === 'undefined') return
        observer = new ResizeObserver(entries => {
            const entry = entries[0]
            if (entry) width.value = entry.contentRect.width / rootFontSizePx()
        })
        observer.observe(target.value)
    })

    onBeforeUnmount(() => {
        observer?.disconnect()
        observer = null
    })

    return readonly(width)
}
