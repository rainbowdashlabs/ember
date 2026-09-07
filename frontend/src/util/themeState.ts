/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'

function darkClassPresent(): boolean {
    return !import.meta.server && document.documentElement.classList.contains('dark')
}

/**
 * How often the theme has repainted the page.
 *
 * <p>A theme change writes CSS variables on the root element and flips the dark class there.
 * Nothing a component holds changes with it, so anything that has read a colour back from the
 * browser is left with the answer for the theme before. Those readers watch this counter.
 *
 * <p>Module level rather than provided, because the alternative is one mutation observer per
 * reader on the root element: a list carries a hundred badges and the toggle is pressed once.
 */
const repaints = ref(0)

const dark = ref(darkClassPresent())

/** Rises by one every time the theme's colours are written to the root element. */
export const themeRevision = readonly(repaints)

/** Whether the page is painted in the dark mode, answered again after every repaint. */
export const darkThemeActive = readonly(dark)

/** Announces that the theme's colours have just been rewritten. */
export function themeRepainted(): void {
    dark.value = darkClassPresent()
    repaints.value += 1
}
