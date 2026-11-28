/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import type {SidebarCounts} from '@/api/generated/schema'
import {useSidebarCounts} from './useSidebarCounts'

const getSidebarCounts = vi.hoisted(() => vi.fn())

vi.mock('@/api/sidebar', () => ({getSidebarCounts}))

function counts(notifications: number): SidebarCounts {
    return {
        notifications,
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
    }
}

/**
 * The station sidebar's counts: the answer to the latest read wins, however the answers arrive, so a
 * slow read cannot bring back a notification that was already marked read.
 */
describe('useSidebarCounts', () => {
    it('drops an older answer that arrives after a newer one', async () => {
        let answerOld: (value: SidebarCounts) => void = () => {}
        getSidebarCounts
            .mockReturnValueOnce(new Promise<SidebarCounts>(resolve => { answerOld = resolve }))
            .mockResolvedValueOnce(counts(0))
        const {counts: shown, refresh} = useSidebarCounts()

        const older = refresh()
        await refresh()
        answerOld(counts(5))
        await older

        expect(shown.value.notifications).toBe(0)
    })
})
