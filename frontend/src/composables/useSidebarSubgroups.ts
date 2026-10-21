/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import {getItem, setItem} from '@/api/storage'

const STORAGE_KEY = 'sidebar_subgroups'

function readStored(): Record<string, boolean> {
    try {
        const parsed: unknown = JSON.parse(getItem(STORAGE_KEY) ?? '{}')
        return parsed && typeof parsed === 'object' ? parsed as Record<string, boolean> : {}
    } catch {
        return {}
    }
}

/**
 * Which subgroups of the sidebar the reader opened or closed by hand, kept per browser so a section
 * looks the same each time it is opened. A subgroup nobody touched has no entry and follows the page.
 */
export function useSidebarSubgroups() {
    const chosen = useState<Record<string, boolean>>('useSidebarSubgroups', () => ({}))
    const restored = useState('useSidebarSubgroups.restored', () => false)

    /** Takes up what this browser remembers; called once the component showing a subgroup is mounted. */
    function restore() {
        if (restored.value) return
        chosen.value = readStored()
        restored.value = true
    }

    function setOpen(key: string, open: boolean) {
        chosen.value = {...chosen.value, [key]: open}
        setItem(STORAGE_KEY, JSON.stringify(chosen.value))
    }

    return {
        chosen: readonly(chosen),
        restore,
        setOpen,
    }
}
