/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {AuthBucket, HourlyTrafficResponse} from './generated/schema'

export interface TrafficQuery {
    from: string
    to: string
    auth?: AuthBucket
}

/** Instance-admin view of every station + global bucket inside the window. */
export async function getAdminHourly(query: TrafficQuery): Promise<HourlyTrafficResponse> {
    const res = await client.get<HourlyTrafficResponse>('/admin/traffic/hourly', {params: query})
    return res.data
}

/** Station-scoped view - the caller's own station only. */
export async function getStationHourly(query: TrafficQuery): Promise<HourlyTrafficResponse> {
    const res = await client.get<HourlyTrafficResponse>('/station/traffic/hourly', {params: query})
    return res.data
}
