/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, watch, type Ref} from 'vue'
import type {PageFileListing} from '@/api/pageManage'

/**
 * Checkbox selection for the file grid, including shift-click range selection and the
 * multi-select mode toggle.
 */
export function usePageFileSelection(pagedFiles: Ref<PageFileListing[]>, activeFolder: Ref<number | null>) {
    const selectedIds = ref<number[]>([])
    const multiSelect = ref(false)
    const lastCheckedIndex = ref<number | null>(null)

    function toggleSelected(id: number, value: boolean, shift: boolean, index: number) {
        if (shift && lastCheckedIndex.value !== null && lastCheckedIndex.value !== index) {
            const lo = Math.min(lastCheckedIndex.value, index)
            const hi = Math.max(lastCheckedIndex.value, index)
            const rangeIds = pagedFiles.value.slice(lo, hi + 1).map(e => e.file.id)
            if (value) {
                const merged = new Set(selectedIds.value)
                rangeIds.forEach(rid => merged.add(rid))
                selectedIds.value = Array.from(merged)
            } else {
                selectedIds.value = selectedIds.value.filter(x => !rangeIds.includes(x))
            }
        } else if (value) {
            if (!selectedIds.value.includes(id)) selectedIds.value = [...selectedIds.value, id]
        } else {
            selectedIds.value = selectedIds.value.filter(x => x !== id)
        }
        lastCheckedIndex.value = index
    }

    function clearSelection() {
        selectedIds.value = []
        lastCheckedIndex.value = null
    }

    function toggleMultiSelect() {
        multiSelect.value = !multiSelect.value
        if (!multiSelect.value) clearSelection()
    }

    watch(activeFolder, clearSelection)

    return {selectedIds, multiSelect, toggleSelected, clearSelection, toggleMultiSelect}
}
