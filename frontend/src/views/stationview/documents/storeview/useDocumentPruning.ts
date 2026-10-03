/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {documents as documentsApi} from '@/api'
import type {DocumentFilter} from '@/api/documents'

/**
 * Choosing several documents and deleting them in one go, typically what is left of people who have
 * gone. The choice can reach past the page in front of the reader to every document the filter
 * matches, and nothing is removed before the reader confirms it.
 *
 * @param filter what the store is narrowed to now, which "every one" means
 */
export function useDocumentPruning(filter: () => DocumentFilter) {
    const selected: Ref<number[]> = ref([])
    const confirming = ref(false)
    const busy = ref(false)

    async function selectAll() {
        busy.value = true
        try {
            selected.value = await documentsApi.listIds(filter())
        } finally {
            busy.value = false
        }
    }

    function clear() {
        selected.value = []
    }

    /**
     * Deletes what is chosen.
     *
     * @return how many were deleted
     */
    async function prune(): Promise<number> {
        busy.value = true
        try {
            const count = selected.value.length
            await documentsApi.prune(selected.value)
            selected.value = []
            confirming.value = false
            return count
        } finally {
            busy.value = false
        }
    }

    return {selected, confirming, busy, selectAll, clear, prune}
}
