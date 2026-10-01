/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {MonitoringCounts} from './generated/schema'

/**
 * What is waiting for whoever runs the instance.
 *
 * <p>Shown as numbers in the sidebar so that nothing shown means nothing to do, rather than an operator
 * having to open three pages to find out whether anything is on them.
 */
export async function getMonitoringCounts(): Promise<MonitoringCounts> {
    const res = await client.get<MonitoringCounts>('/admin/monitoring-counts')
    return res.data
}
