/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import {getMonitoringCounts} from '@/api/adminMonitoring'
import type {MonitoringCounts} from '@/api/generated/schema'

/**
 * What the instance has waiting, held once for whoever asks.
 *
 * <p>Shared rather than per-component, because the sidebar and the pages under it would otherwise
 * each ask for the same numbers. A failure is swallowed: a sidebar that cannot count is a sidebar
 * without badges, not a broken screen. Read again on every move inside the administration and after
 * anything was acknowledged, never on a timer.
 */
export function useMonitoringCounts() {
    const counts = useState<MonitoringCounts>('useMonitoringCounts', () => ({
        problemLog: 0,
        problemReports: 0,
        beaconActive: false,
        beaconFaults: 0,
        beaconReports: 0,
        failedMails: 0,
    }))
    const latestRead = useState<number>('useMonitoringCounts.latestRead', () => 0)

    /**
     * Reads the counts again, keeping the last ones when that fails. An answer to an older read that
     * arrives after a newer one was asked for is dropped, so a slow answer cannot bring back a count
     * that was already acknowledged away.
     */
    async function refresh() {
        const read = ++latestRead.value
        await getMonitoringCounts()
            .then(found => { if (read === latestRead.value) counts.value = found })
            .catch(() => {})
    }

    return {counts: readonly(counts), refresh}
}
