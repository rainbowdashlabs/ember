/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {TrackingStatus, type DataTracking, type TableEntry} from '@/api/dataTracking'

/** One tracked database table as the inventory lists it. */
export interface TrackingRow {
    name: string
    entry: TableEntry
    unverifiedColumns: number
    /** Whether any of its contexts or columns still waits for somebody to look at it. */
    needsReview: boolean
}

function rowOf(name: string, entry: TableEntry): TrackingRow {
    const unverifiedColumns = entry.columns.filter(column => !column.verified).length
    const statuses = [entry.stationTransfer.status, entry.gdprExport.status, entry.gdprDeletion.status]
    return {
        name,
        entry,
        unverifiedColumns,
        needsReview: unverifiedColumns > 0 || statuses.includes(TrackingStatus.UNVERIFIED),
    }
}

export function trackingRowsOf(tracking: DataTracking | null): TrackingRow[] {
    if (!tracking) return []
    return Object.entries(tracking.tables).map(([name, entry]) => rowOf(name, entry))
}

/** The names of the table's columns whose name or description holds the query. */
export function matchedColumnsOf(entry: TableEntry, query: string): string[] {
    const needle = query.trim().toLowerCase()
    if (!needle) return []
    return entry.columns
        .filter(column => column.name.toLowerCase().includes(needle) || (column.description ?? '').toLowerCase().includes(needle))
        .map(column => column.name)
}

/** The words the search looks through beyond the table's name: its description and its columns. */
export function searchTextOf(row: TrackingRow): string {
    const columns = row.entry.columns.map(column => `${column.name} ${column.description ?? ''}`)
    return [row.entry.description ?? '', ...columns].join(' ')
}
