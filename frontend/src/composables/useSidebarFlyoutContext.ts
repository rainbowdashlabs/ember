/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide} from 'vue'
import type {InjectionKey} from 'vue'

/**
 * Handlers of the flyout panel a sidebar entry is rendered in.
 *
 * <p>Nested panels are teleported to the body, so they are not DOM descendants of the panel
 * that owns them. Moving the cursor into a nested panel therefore raises {@code mouseleave} on
 * every ancestor panel and would close them out from under it. A nested panel forwards its own
 * hover events through this context so its ancestors keep treating the cursor as inside them,
 * and calls {@link SidebarFlyoutContext#close} to dismiss the whole chain once a link is taken.
 */
export interface SidebarFlyoutContext {
    enter(): void

    leave(): void

    close(): void
}

export const SIDEBAR_FLYOUT_KEY: InjectionKey<SidebarFlyoutContext | null> = Symbol('sidebar-flyout')

export function provideSidebarFlyoutContext(context: SidebarFlyoutContext) {
    provide(SIDEBAR_FLYOUT_KEY, context)
}

/**
 * The enclosing flyout panel, or {@code null} when the entry renders in the sidebar rail itself.
 */
export function useSidebarFlyoutParent(): SidebarFlyoutContext | null {
    return inject(SIDEBAR_FLYOUT_KEY, null)
}

/**
 * Whether this entry renders inside a flyout panel rather than in the sidebar rail. Inside a
 * flyout, entries always render their full labels and nested subgroups open as further flyouts
 * instead of expanding inline.
 */
export function useSidebarInFlyout(): boolean {
    return useSidebarFlyoutParent() !== null
}
