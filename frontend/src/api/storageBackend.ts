/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {queryParams} from './crud'
import type {
    AuditEntryResponse,
    BackendOverrideResponse,
    BackendRequest,
    BackendSummary,
    components,
    InstanceMigrateRequest,
    InstanceMigrationStatusResponse,
    MigrationResponse,
    ProbeResult,
} from './generated/schema'

type Schemas = components['schemas']

export type StorageBackendTypeName = Schemas['StorageBackendType']

export const StorageBackendType = {
    LOCAL: 'LOCAL',
    S3: 'S3',
    SMB: 'SMB',
    SFTP: 'SFTP',
} as const satisfies Record<StorageBackendTypeName, StorageBackendTypeName>

export type StorageAuditActionName = Schemas['StorageAuditAction']

export const StorageAuditAction = {
    CREATED: 'CREATED',
    UPDATED: 'UPDATED',
    DELETED: 'DELETED',
    PROBE_OK: 'PROBE_OK',
    PROBE_FAILED: 'PROBE_FAILED',
    MIGRATION_STARTED: 'MIGRATION_STARTED',
    MIGRATION_COMPLETED: 'MIGRATION_COMPLETED',
    MIGRATION_FAILED: 'MIGRATION_FAILED',
    REJECTED: 'REJECTED',
    INSTANCE_DEFAULT_UPDATED: 'INSTANCE_DEFAULT_UPDATED',
    INSTANCE_MIGRATION_STARTED: 'INSTANCE_MIGRATION_STARTED',
    INSTANCE_MIGRATION_COMPLETED: 'INSTANCE_MIGRATION_COMPLETED',
    INSTANCE_MIGRATION_FAILED: 'INSTANCE_MIGRATION_FAILED',
    POLICY_CHANGED: 'POLICY_CHANGED',
} as const satisfies Record<StorageAuditActionName, StorageAuditActionName>

export type StorageAuditOutcomeName = Schemas['StorageAuditOutcome']

export const StorageAuditOutcome = {
    OK: 'OK',
    FAILED: 'FAILED',
} as const satisfies Record<StorageAuditOutcomeName, StorageAuditOutcomeName>

/**
 * What is behind a station's files, on whose word, and what is still the station's to change.
 *
 * A station under an association may be standing on the association's storage and may have been put there
 * by somebody else, so the answer says who decided rather than only what was decided.
 */
export async function getStationBackend(): Promise<BackendOverrideResponse> {
    const {data} = await client.get<BackendOverrideResponse>('/station/storage/backend')
    return data
}

export async function probeStationBackend(): Promise<ProbeResult> {
    const {data} = await client.post<ProbeResult>('/station/storage/backend/probe')
    return data
}

export async function probeStationBackendConfig(request: BackendRequest): Promise<ProbeResult> {
    const {data} = await client.post<ProbeResult>('/station/storage/backend/probe-config', request)
    return data
}

export async function applyStationBackend(request: BackendRequest): Promise<MigrationResponse> {
    const {data} = await client.post<MigrationResponse>('/station/storage/backend/apply', request)
    return data
}

export async function getStationStorageAudit(before?: string, limit = 50): Promise<AuditEntryResponse[]> {
    const {data} = await client.get<AuditEntryResponse[]>('/station/storage/audit', {
        params: queryParams({limit, before: before || undefined}),
    })
    return data
}

export async function getInstanceBackend(): Promise<BackendSummary> {
    const {data} = await client.get<BackendSummary>('/admin/storage/backend')
    return data
}

export async function probeInstanceBackend(): Promise<ProbeResult> {
    const {data} = await client.post<ProbeResult>('/admin/storage/backend/probe')
    return data
}

export async function probeInstanceBackendConfig(request: BackendRequest): Promise<ProbeResult> {
    const {data} = await client.post<ProbeResult>('/admin/storage/backend/probe-config', request)
    return data
}

export async function applyInstanceBackend(request: InstanceMigrateRequest): Promise<MigrationResponse> {
    const {data} = await client.post<MigrationResponse>('/admin/storage/backend/apply', request)
    return data
}

export async function getInstanceMigrationStatus(): Promise<InstanceMigrationStatusResponse> {
    const {data} = await client.get<InstanceMigrationStatusResponse>('/admin/storage/backend/apply/status')
    return data
}

export async function getInstanceStorageAudit(
    options: {before?: string; stationUid?: string; limit?: number} = {},
): Promise<AuditEntryResponse[]> {
    const {data} = await client.get<AuditEntryResponse[]>('/admin/storage/audit', {
        params: queryParams({
            limit: options.limit ?? 50,
            before: options.before || undefined,
            stationUid: options.stationUid || undefined,
        }),
    })
    return data
}
