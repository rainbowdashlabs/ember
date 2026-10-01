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
 * each ask for the same three numbers. A failure is swallowed: a sidebar that cannot count is a sidebar
 * without badges, not a broken screen.
 */
export function useMonitoringCounts() {
    const counts = useState<MonitoringCounts>('useMonitoringCounts', () => ({
        problemLog: 0,
        problemReports: 0,
        beaconActive: false,
        beaconFaults: 0,
        beaconReports: 0,
    }))

    /** Reads the counts again, keeping the last ones when that fails. */
    async function refresh() {
        await getMonitoringCounts().then(found => { counts.value = found }).catch(() => {})
    }

    return {counts: readonly(counts), refresh}
}
