/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

// -- Backend payload shapes (kept in sync with dev.chojo.ember.tracking.*) --

export const TrackingStatus = {
    TRACKED: 'TRACKED',
    IGNORED: 'IGNORED',
    UNVERIFIED: 'UNVERIFIED',
} as const

export type TrackingStatusName = (typeof TrackingStatus)[keyof typeof TrackingStatus]

export interface ColumnEntry {
    name: string
    type: string
    nullable: boolean
    verified: boolean
    /** Mirror of the PG COMMENT ON COLUMN text; not part of the hash. */
    description?: string | null
}

export interface ForeignKey {
    column: string
    refTable: string
    refColumn: string
    onDelete: string
}

export interface Lookup {
    via: string
    pick: string
    emitAs: string
}

export interface CustomScope {
    viaTable: string
    viaColumn: string
    refColumn: string
    distinct: boolean
}

export interface TransferContext {
    status: TrackingStatusName
    reason?: string | null
    ignoredColumns?: string[]
    rationale?: string | null
}

export interface IdentityColumn {
    column: string
    type: string
}

export interface GdprExportContext {
    status: TrackingStatusName
    reason?: string | null
    identityColumns?: IdentityColumn[]
    ignoredColumns?: string[]
}

export interface DeletionStrategy {
    column: string
    strategy: string
    reason?: string
    legalBasis?: string | null
}

export interface GdprDeletionContext {
    status: TrackingStatusName
    reason?: string | null
    strategies?: DeletionStrategy[]
}

export interface TableEntry {
    feature?: string | null
    scope?: string | null
    tableHash: string
    columns: ColumnEntry[]
    foreignKeys?: ForeignKey[]
    lookups?: Lookup[] | null
    outputShape?: 'ROWS' | 'SINGLE' | 'FLAT' | null
    flatField?: string | null
    customScope?: CustomScope | null
    stationTransfer: TransferContext
    gdprExport: GdprExportContext
    gdprDeletion: GdprDeletionContext
    /** Mirror of the PG COMMENT ON TABLE text; not part of the hash. */
    description?: string | null
}

export interface DataTracking {
    version: number
    schemaHash: string
    generatedAt: string
    tables: Record<string, TableEntry>
    fileStores?: Record<string, unknown>
}

export interface StatusCounts {
    tracked: number
    ignored: number
    unverified: number
}

export interface DataTrackingSummary {
    totalTables: number
    totalColumns: number
    verifiedColumns: number
    stationTransfer: StatusCounts
    gdprExport: StatusCounts
    gdprDeletion: StatusCounts
}

export interface TableUpdatePayload {
    feature?: string | null
    columnVerified?: Record<string, boolean>
    stationTransfer?: TransferContext | null
    gdprExport?: GdprExportContext | null
    gdprDeletion?: GdprDeletionContext | null
}

// -- API calls --

export async function getDataTracking(): Promise<DataTracking> {
    const res = await client.get<DataTracking>('/admin/data-tracking')
    return res.data
}

export async function getDataTrackingSummary(): Promise<DataTrackingSummary> {
    const res = await client.get<DataTrackingSummary>('/admin/data-tracking/summary')
    return res.data
}

export async function updateDataTrackingTable(
    table: string,
    payload: TableUpdatePayload,
): Promise<TableEntry> {
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
