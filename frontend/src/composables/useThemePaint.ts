/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {themePaint} from '@/util/themeState'

/**
 * What the theme last painted, for whatever has to follow it by hand: charts drawn on a canvas, and
 * colours read back from the browser.
 *
 * @return `dark`, whether the page is painted dark, and `revision`, which rises by one with every
 *         repaint
 */
export function useThemePaint() {
    const paint = themePaint()
    return {
        dark: computed(() => paint.value.dark),
        revision: computed(() => paint.value.revision),
    }
}
