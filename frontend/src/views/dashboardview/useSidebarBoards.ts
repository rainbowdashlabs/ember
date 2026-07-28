/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import type {Board} from '@/api/boards'
import {listBoards} from '@/api/boards'

const boards = ref<Board[]>([])

/**
 * Boards shown as sub-links of the station sidebar's boards section.
 *
 * <p>The station shell refreshes the list whenever the session becomes ready; the sidebar
 * section itself only reads it.
 */
export function useSidebarBoards() {
    async function refresh() {
        try {
            boards.value = await listBoards(true)
        } catch {
            boards.value = []
        }
    }

    return {
        boards: readonly(boards),
        refresh,
    }
}
