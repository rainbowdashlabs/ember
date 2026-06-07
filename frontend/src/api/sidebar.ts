/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface SidebarCounts {
    notifications: number
    requirements: number
    pendingChanges: number
    pendingRegistrations: number
    lendingRequests: number
    federationRequests: number
    openEvents: number
    waitingListEntries: number
    lostAndFoundPending: number
}

export async function getSidebarCounts(): Promise<SidebarCounts> {
    const res = await client.get<SidebarCounts>('/sidebar-counts')
    return res.data
}
