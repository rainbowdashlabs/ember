/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {components, DataTracking, Summary, TableEntry, TableUpdate} from './generated/schema'

export type TrackingStatusName = components['schemas']['TrackingStatus']

export const TrackingStatus = {
    TRACKED: 'TRACKED',
    IGNORED: 'IGNORED',
    UNVERIFIED: 'UNVERIFIED',
} as const satisfies Record<TrackingStatusName, TrackingStatusName>

export async function getDataTracking(): Promise<DataTracking> {
    const res = await client.get<DataTracking>('/admin/data-tracking')
    return res.data
}

export async function getDataTrackingSummary(): Promise<Summary> {
    const res = await client.get<Summary>('/admin/data-tracking/summary')
    return res.data
}

export async function updateDataTrackingTable(table: string, payload: TableUpdate): Promise<TableEntry> {
    const res = await client.put<TableEntry>(`/admin/data-tracking/tables/${encodeURIComponent(table)}`, payload)
    return res.data
}

export async function verifyAllColumns(table: string): Promise<TableEntry> {
    const res = await client.post<TableEntry>(
        `/admin/data-tracking/tables/${encodeURIComponent(table)}/verify-columns`,
        {},
    )
    return res.data
}
