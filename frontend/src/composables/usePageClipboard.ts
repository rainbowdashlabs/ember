/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'

export interface ClipboardEntry {
    type: 'row' | 'cell'
    data: unknown
    isCut: boolean
    removeCallback?: () => void
}

const clipboard = ref<ClipboardEntry | null>(null)

export function usePageClipboard() {
    function copyRow(row: unknown) {
        clipboard.value = {type: 'row', data: structuredClone(row), isCut: false}
    }

    function cutRow(row: unknown, removeCallback: () => void) {
        clipboard.value = {type: 'row', data: structuredClone(row), isCut: true, removeCallback}
    }

    function pasteRow(): unknown | null {
        if (!clipboard.value || clipboard.value.type !== 'row') return null
        const entry = clipboard.value
        if (entry.isCut && entry.removeCallback) {
            entry.removeCallback()
        }
        const data = structuredClone(entry.data)
        if (entry.isCut) {
            clipboard.value = null
        }
        return data
    }

    function copyCell(cell: unknown) {
        clipboard.value = {type: 'cell', data: structuredClone(cell), isCut: false}
    }

    function cutCell(cell: unknown, removeCallback: () => void) {
        clipboard.value = {type: 'cell', data: structuredClone(cell), isCut: true, removeCallback}
    }

    function pasteCell(): unknown | null {
        if (!clipboard.value || clipboard.value.type !== 'cell') return null
        const entry = clipboard.value
        if (entry.isCut && entry.removeCallback) {
            entry.removeCallback()
        }
        const data = structuredClone(entry.data)
        if (entry.isCut) {
            clipboard.value = null
        }
        return data
    }

    const hasClipboard = computed(() => clipboard.value != null)
    const clipboardType = computed(() => clipboard.value?.type ?? null)

    return {
        copyRow,
        cutRow,
        pasteRow,
        copyCell,
        cutCell,
        pasteCell,
        hasClipboard,
        clipboardType,
    }
}
