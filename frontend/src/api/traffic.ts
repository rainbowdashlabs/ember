/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * Auth bucket the backend classifies each request into. Matches
 * {@code dev.chojo.ember.feature.traffic.entity.AuthBucket}.
 */
export const AuthBucket = {
    AUTHENTICATED: 'AUTHENTICATED',
    UNAUTHENTICATED: 'UNAUTHENTICATED',
    FEDERATION: 'FEDERATION',
} as const

export type AuthBucketName = (typeof AuthBucket)[keyof typeof AuthBucket]

/**
 * One row of {@code station_traffic_hourly} as exposed by the admin / station endpoints.
 * {@code stationId} is the public UUID string (serialized via {@code StationIdModule} on the
 * backend), or {@code null} for instance-global rows.
 */
export interface HourlyTrafficRow {
    hour: string
    stationId: string | null
    auth: AuthBucketName
    ingressBytes: number
    egressBytes: number
    requests: number
}

export interface HourlyTrafficResponse {
    rows: HourlyTrafficRow[]
}

export interface TrafficQuery {
    from: string
    to: string
    stationId?: string
    auth?: AuthBucketName
}

/** Instance-admin view of every station + global bucket inside the window. */
export async function getAdminHourly(query: TrafficQuery): Promise<HourlyTrafficResponse> {
    const res = await client.get<HourlyTrafficResponse>('/admin/traffic/hourly', {params: query})
    return res.data
}

/** Station-scoped view - the caller's own station only. */
export async function getStationHourly(
    query: Omit<TrafficQuery, 'stationId'>,
): Promise<HourlyTrafficResponse> {
    const res = await client.get<HourlyTrafficResponse>('/station/traffic/hourly', {params: query})
    return res.data
}
