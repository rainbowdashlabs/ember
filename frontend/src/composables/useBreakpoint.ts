/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {breakpointsTailwind, useBreakpoints} from '@vueuse/core'

/**
 * Where the window stands against the Tailwind breakpoints the templates are written in: below `md`
 * is a phone, `lg` and above is a desktop. Both read false on the server.
 */
export function useBreakpoint() {
    const breakpoints = useBreakpoints(breakpointsTailwind)
    return {
        isMobile: breakpoints.smaller('md'),
        isDesktop: breakpoints.greaterOrEqual('lg'),
    }
}
