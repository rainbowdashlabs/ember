/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, type DeepReadonly, type ShallowRef} from 'vue'
import {browserShallowRef} from '@/util/browserState'
import {forgetGlyphSurfaces} from '@/util/glyphOutline'

/** How often the theme has repainted the page, and whether it painted it dark the last time. */
export interface ThemePaint {
    revision: number
    dark: boolean
}

/**
 * What the theme last painted.
 *
 * <p>A theme change writes CSS variables on the root element and flips the dark class there.
 * Nothing a component holds changes with it, so anything that has read a colour back from the
 * browser is left with the answer for the theme before. Those readers watch the revision.
 *
 * <p>Shared rather than provided, because the alternative is one mutation observer per reader on
 * the root element: a list carries a hundred badges and the toggle is pressed once. Only the browser
 * paints, so it is browser state: every server render reads it light, and the client plugin's first
 * paint is what says dark.
 */
const paint = browserShallowRef<ThemePaint>({revision: 0, dark: false})

/** Read-only view of what the theme last painted. */
export function themePaint(): DeepReadonly<ShallowRef<ThemePaint>> {
    return readonly(paint)
}

/** Announces that the theme's colours have just been rewritten to the root element. */
export function themeRepainted(): void {
    forgetGlyphSurfaces()
    paint.value = {
        revision: paint.value.revision + 1,
        dark: document.documentElement.classList.contains('dark'),
    }
}
