/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import type {SidebarCounts} from '@/api/sidebar'
import {getSidebarCounts} from '@/api/sidebar'

const counts = ref<SidebarCounts>({
    notifications: 0,
    requirements: 0,
    pendingChanges: 0,
    pendingRegistrations: 0,
    lendingRequests: 0,
    federationRequests: 0,
    openEvents: 0,
    waitingListEntries: 0,
    lostAndFoundPending: 0,
    myInventoryCount: 0,
    pendingExchanges: 0,
})

export function useSidebarCounts() {
    async function refresh() {
        try {
            counts.value = await getSidebarCounts()
        } catch {
            // ignore — endpoint may not be available yet
        }
    }

    return {
        counts: readonly(counts),
        refresh,
    }
}
