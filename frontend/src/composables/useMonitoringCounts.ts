/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import {getMonitoringCounts, type MonitoringCounts} from '@/api/adminMonitoring'

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

    async function refresh() {
        try {
            counts.value = await getMonitoringCounts()
        } catch {
            // An operator who cannot be counted for is still an operator who can navigate.
        }
    }

    return {counts: readonly(counts), refresh}
}
