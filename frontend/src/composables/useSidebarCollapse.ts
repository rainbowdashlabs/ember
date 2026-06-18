/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {getItem, setItem} from '@/api/storage'

const STORAGE_KEY = 'sidebar_collapsed'

const collapsed = ref<boolean>(getItem(STORAGE_KEY) === '1')

function setCollapsed(value: boolean) {
    collapsed.value = value
    setItem(STORAGE_KEY, value ? '1' : '0')
}

function toggle() {
    setCollapsed(!collapsed.value)
}

/**
 * Reactive state for the desktop sidebar collapse. Persisted to localStorage so the user's
 * preference survives page reloads. Has no effect on mobile (where the sidebar is a slide-in
 * drawer instead).
 */
export function useSidebarCollapse() {
    return {
        collapsed: readonly(collapsed),
        setCollapsed,
        toggle,
    }
}
