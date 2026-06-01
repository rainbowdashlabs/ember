/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { readonly, ref } from 'vue'
import type { FederatedBoardBookmark } from '@/api/federatedBoards'
import { listBookmarks } from '@/api/federatedBoards'

const bookmarks = ref<FederatedBoardBookmark[]>([])

export function useFederatedBoardBookmarks() {
    async function refresh() {
        try {
            bookmarks.value = await listBookmarks()
        } catch {
            // ignore
        }
    }

    return {
        bookmarks: readonly(bookmarks),
        refresh,
    }
}
