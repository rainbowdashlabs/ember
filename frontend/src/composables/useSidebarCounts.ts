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
    const latestRead = useState<number>('useSidebarCounts.latestRead', () => 0)

    /**
     * Reads the counts again, keeping the last ones when the endpoint is not available yet. An answer
     * to an older read that arrives after a newer one was asked for is dropped, so a slow answer cannot
     * bring back a count that was already acknowledged away.
     */
    async function refresh() {
        const read = ++latestRead.value
        await getSidebarCounts()
            .then(found => { if (read === latestRead.value) counts.value = found })
            .catch(() => {})
    }

    return {
        counts: readonly(counts),
        refresh,
    }
}
