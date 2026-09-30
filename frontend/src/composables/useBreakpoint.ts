/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {breakpointsTailwind, useBreakpoints} from '@vueuse/core'
import {computed} from 'vue'
import {useHydrated} from '@/composables/useHydrated'

/**
 * Where the window stands against the Tailwind breakpoints the templates are written in: below `md`
 * is a phone, `lg` and above is a desktop.
 *
 * <p>Both read false on the server, and in the browser until the page the server sent has been taken
 * up (see {@link useHydrated}), so a phone gets its narrow layout as a change after hydration rather
 * than as a disagreement with the server's markup.
 */
export function useBreakpoint() {
    const hydrated = useHydrated()
    const breakpoints = useBreakpoints(breakpointsTailwind)
    const phone = breakpoints.smaller('md')
    const desktop = breakpoints.greaterOrEqual('lg')
    return {
        isMobile: computed(() => hydrated.value && phone.value),
        isDesktop: computed(() => hydrated.value && desktop.value),
    }
}
