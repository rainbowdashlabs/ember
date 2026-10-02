/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, inject, readonly, type InjectionKey, type Ref} from 'vue'
import {getItem, setItem} from '@/api/storage'

const STORAGE_KEY = 'sidebar_collapsed'

function collapsedState() {
    return useState('useSidebarCollapse', () => false)
}

/**
 * Puts the reader's stored choice into the state, which starts out expanded like the page the
 * server sends. Called by the client plugin: before the first render where the page was not rendered
 * on the server, after hydration where it was.
 */
export function restoreSidebarCollapse() {
    collapsedState().value = getItem(STORAGE_KEY) === '1'
}

/**
 * Provided by a parent ({@link AppSidebar}) to disable collapsing for its
 * subtree. When the injected value is {@code false}, every consumer of
 * {@link useSidebarCollapse} sees {@code collapsed = false} regardless of the
 * user's stored preference - used by panels (help center) where a collapsed
 * sidebar would hide too much navigation.
 */
export const SIDEBAR_COLLAPSIBLE: InjectionKey<Ref<boolean> | boolean> = Symbol('SidebarCollapsible')

/**
 * Reactive state for the desktop sidebar collapse. Persisted to localStorage so the user's
 * preference survives page reloads. Has no effect on mobile (where the sidebar is a slide-in
 * drawer instead). A parent can provide {@link SIDEBAR_COLLAPSIBLE} set to {@code false}
 * to force-expand the sidebar for its subtree.
 */
export function useSidebarCollapse() {
    const collapsed = collapsedState()
    const collapsibleProvided = inject(SIDEBAR_COLLAPSIBLE, true)

    function setCollapsed(value: boolean) {
        collapsed.value = value
        setItem(STORAGE_KEY, value ? '1' : '0')
    }

    function toggle() {
        setCollapsed(!collapsed.value)
    }

    const effectiveCollapsed = computed(() => {
        const isCollapsible = typeof collapsibleProvided === 'boolean'
            ? collapsibleProvided
            : collapsibleProvided.value
        return isCollapsible ? collapsed.value : false
    })
    return {
        collapsed: readonly(effectiveCollapsed),
        setCollapsed,
        toggle,
    }
}
