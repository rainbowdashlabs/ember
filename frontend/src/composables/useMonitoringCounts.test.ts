/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import type {MonitoringCounts} from '@/api/generated/schema'
import {useMonitoringCounts} from './useMonitoringCounts'

const getMonitoringCounts = vi.hoisted(() => vi.fn())

vi.mock('@/api/adminMonitoring', () => ({getMonitoringCounts}))

function counts(failedMails: number): MonitoringCounts {
    return {problemLog: 0, problemReports: 0, beaconActive: false, beaconFaults: 0, beaconReports: 0, failedMails}
}

/**
 * The sidebar's counts: the answer to the latest read wins, however the answers arrive.
 */
describe('useMonitoringCounts', () => {
    it('drops an older answer that arrives after a newer one', async () => {
        let answerOld: (value: MonitoringCounts) => void = () => {}
        getMonitoringCounts
            .mockReturnValueOnce(new Promise<MonitoringCounts>(resolve => { answerOld = resolve }))
            .mockResolvedValueOnce(counts(0))
        const {counts: shown, refresh} = useMonitoringCounts()

        const older = refresh()
        await refresh()
        answerOld(counts(5))
        await older

        expect(shown.value.failedMails).toBe(0)
    })
})
