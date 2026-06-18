/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, nextTick, ref} from 'vue'

/**
 * JSON-clone instead of structuredClone — the inputs are Vue reactive Proxies whose hidden
 * reactivity metadata makes structuredClone throw. The clipboard data is plain JSON (row
 * definitions with primitives/strings/numbers) so JSON round-tripping is safe and strips any
 * reactivity along the way.
 */
function deepClone<T>(value: T): T {
    return JSON.parse(JSON.stringify(value)) as T
}

export interface ClipboardEntry {
    type: 'row' | 'cell'
    data: unknown
    isCut: boolean
    removeCallback?: () => void
}

const clipboard = ref<ClipboardEntry | null>(null)

export function usePageClipboard() {
    function copyRow(row: unknown) {
        clipboard.value = {type: 'row', data: deepClone(row), isCut: false}
    }

    function cutRow(row: unknown, removeCallback: () => void) {
        clipboard.value = {type: 'row', data: deepClone(row), isCut: true, removeCallback}
    }

    function pasteRow(): unknown | null {
        if (!clipboard.value || clipboard.value.type !== 'row') return null
        const entry = clipboard.value
        const data = deepClone(entry.data)
        if (entry.isCut) {
            clipboard.value = null
            if (entry.removeCallback) nextTick(entry.removeCallback)
        }
        return data
    }

    function copyCell(cell: unknown) {
        clipboard.value = {type: 'cell', data: deepClone(cell), isCut: false}
    }

    function cutCell(cell: unknown, removeCallback: () => void) {
        clipboard.value = {type: 'cell', data: deepClone(cell), isCut: true, removeCallback}
    }

    function pasteCell(): unknown | null {
        if (!clipboard.value || clipboard.value.type !== 'cell') return null
        const entry = clipboard.value
        const data = deepClone(entry.data)
        if (entry.isCut) {
            clipboard.value = null
            if (entry.removeCallback) nextTick(entry.removeCallback)
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
