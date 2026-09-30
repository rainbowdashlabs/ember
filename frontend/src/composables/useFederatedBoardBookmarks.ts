/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import {listBookmarks, type FederatedBoardBookmark} from '@/api/federatedBoards'

/** The boards of partner stations the reader keeps in the sidebar. */
export function useFederatedBoardBookmarks() {
    const bookmarks = useState<FederatedBoardBookmark[]>('useFederatedBoardBookmarks', () => [])

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
