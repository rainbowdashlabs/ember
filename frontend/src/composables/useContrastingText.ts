/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onMounted, ref, watch, type Ref} from 'vue'
import {compositeOver, contrastingTextColor, parseCssColor, type Rgb, type Rgba} from '@/util/contrastColor'
import {themeRevision} from '@/util/themeState'

const PAPER: Rgb = [255, 255, 255]

/**
 * What the browser really paints where an element sits: its own background over everything
 * showing through it, down to the first layer that hides what is behind it.
 *
 * <p>Every colour here is worth reading rather than assuming. A station picks its own palette, and
 * the badges are translucent, so the same colour sits on a dark page and a light one and needs
 * different letters on each.
 */
function paintedBackground(element: HTMLElement): Rgb {
    const layers: Rgba[] = []
    for (let node: HTMLElement | null = element; node; node = node.parentElement) {
        const layer = parseCssColor(getComputedStyle(node).backgroundColor)
        if (!layer || layer[3] === 0) continue
        layers.push(layer)
        if (layer[3] >= 1) break
    }
    let painted = PAPER
    for (let index = layers.length - 1; index >= 0; index--) {
        painted = compositeOver(layers[index]!, painted)
    }
    return painted
}

/**
 * The colour an element's letters need to stay readable on the background behind them.
 *
 * <p>Read from the page rather than declared, because the colours are the station's to choose and
 * only the browser knows what a utility class and an opacity modifier came out as. It is answered
 * again whenever the theme repaints, which changes the background without touching a single prop:
 * a badge that measured itself in the dark theme would otherwise keep white letters in the light
 * one.
 *
 * @param element    the element whose background decides the colour
 * @param background what the caller can change about that background, watched for its own sake
 */
export function useContrastingText(
    element: Ref<HTMLElement | null>,
    background: () => unknown,
): Readonly<Ref<string>> {
    const textColor = ref('')

    function update() {
        const node = element.value
        if (!node) return
        const [r, g, b] = paintedBackground(node)
        textColor.value = contrastingTextColor(r, g, b)
    }

    onMounted(update)
    watch(background, () => requestAnimationFrame(update))
    watch(themeRevision, update)

    return textColor
}
