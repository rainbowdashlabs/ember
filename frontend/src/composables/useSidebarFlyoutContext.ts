/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide} from 'vue'

/**
 * Context flag shared via provide / inject so leaf sidebar components
 * ({@code SidebarLink}, {@code SidebarSubGroup}) know whether they are being
 * rendered inside a flyout panel versus inside the actual sidebar rail.
 * Inside a flyout they always render their full labels and let nested
 * subgroups open as further flyouts instead of expanding inline.
 */
export const SIDEBAR_FLYOUT_KEY = Symbol('sidebar-in-flyout')

export function provideSidebarFlyoutContext() {
    provide(SIDEBAR_FLYOUT_KEY, true)
}

export function useSidebarInFlyout(): boolean {
    return inject<boolean>(SIDEBAR_FLYOUT_KEY, false)
}
