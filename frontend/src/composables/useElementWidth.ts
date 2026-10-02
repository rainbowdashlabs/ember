/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useElementSize} from '@vueuse/core'
import {computed, type Ref} from 'vue'

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
    const {width} = useElementSize(target)
    return computed(() => width.value > 0 ? width.value / rootFontSizePx() : null)
}
