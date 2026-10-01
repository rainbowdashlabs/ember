/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import {getSidebarCounts} from '@/api/sidebar'
import type {SidebarCounts} from '@/api/generated/schema'

/** The numbers beside the station sidebar's entries, held once for the sidebar and the pages under it. */
export function useSidebarCounts() {
    const counts = useState<SidebarCounts>('useSidebarCounts', () => ({
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
        openMovements: 0,
        procedureCount: 0,
    }))

    async function refresh() {
        try {
            counts.value = await getSidebarCounts()
        } catch {
            // ignore - endpoint may not be available yet
        }
    }

    return {
        counts: readonly(counts),
        refresh,
    }
}
