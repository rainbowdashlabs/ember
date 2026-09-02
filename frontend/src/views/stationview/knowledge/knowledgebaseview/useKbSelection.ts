/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch, type Ref} from 'vue'
import type {KbItem} from './useKbItems'

/**
 * Marking several entries of the folder being browsed in order to do one thing to all of them.
 *
 * Folders and articles are marked together, because somebody reordering a branch marks both. The
 * mark travels as the entry key the listing already uses, so the two views agree on what is marked
 * without either of them having to know what is behind a row.
 *
 * Only entries of the folder being browsed can be marked. Search hits cannot: a hit is reached
 * without walking the folders whose permissions decide what may be done to it, which is why the
 * listing gives a hit no managing actions either.
 *
 * @param items         the entries of the open folder, in the order they are drawn
 * @param currentFolder the folder being browsed, so leaving it drops the mark
 */
export function useKbSelection(items: Ref<KbItem[]>, currentFolder: Ref<unknown>) {
    const selecting = ref(false)
    const selectedKeys = ref<string[]>([])
    const lastIndex = ref<number | null>(null)

    /** Only an entry of this station can be acted on, so partner entries are never markable. */
    const selectableKeys = computed(() =>
        items.value
            .filter(item => item.key.startsWith('folder-') || item.key.startsWith('file-'))
            .map(item => item.key))

    const selectedFolderIds = computed(() =>
        selectedKeys.value.filter(key => key.startsWith('folder-')).map(key => Number(key.slice('folder-'.length))))

    const selectedFileIds = computed(() =>
        selectedKeys.value.filter(key => key.startsWith('file-')).map(key => Number(key.slice('file-'.length))))

    const selectedCount = computed(() => selectedKeys.value.length)

    function isSelected(key: string): boolean {
        return selectedKeys.value.includes(key)
    }

    /**
     * Marks or unmarks one entry, or, held with shift, everything between it and the last one
     * touched. The range runs over the drawn order, so it is the stretch the reader saw.
     */
    function toggle(key: string, value: boolean, shift: boolean) {
        const order = selectableKeys.value
        const index = order.indexOf(key)
        if (index < 0) return
        const range = shift && lastIndex.value !== null && lastIndex.value !== index
            ? order.slice(Math.min(lastIndex.value, index), Math.max(lastIndex.value, index) + 1)
            : [key]
        if (value) {
            const merged = new Set(selectedKeys.value)
            range.forEach(entry => merged.add(entry))
            selectedKeys.value = order.filter(entry => merged.has(entry))
        } else {
            selectedKeys.value = selectedKeys.value.filter(entry => !range.includes(entry))
        }
        lastIndex.value = index
    }

    function clear() {
        selectedKeys.value = []
        lastIndex.value = null
    }

    function toggleSelecting() {
        selecting.value = !selecting.value
        if (!selecting.value) clear()
    }

    watch(currentFolder, clear)
    watch(items, () => {
        const available = new Set(selectableKeys.value)
        selectedKeys.value = selectedKeys.value.filter(key => available.has(key))
    })

    return {
        selecting,
        selectedKeys,
        selectedCount,
        selectedFolderIds,
        selectedFileIds,
        isSelected,
        toggle,
        clear,
        toggleSelecting,
    }
}
