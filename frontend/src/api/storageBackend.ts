/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export const StorageBackendType = {
    LOCAL: 'LOCAL',
    S3: 'S3',
    SMB: 'SMB',
    SFTP: 'SFTP',
} as const

export type StorageBackendTypeName = (typeof StorageBackendType)[keyof typeof StorageBackendType]

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
} as const

export type StorageAuditActionName = (typeof StorageAuditAction)[keyof typeof StorageAuditAction]

export const StorageAuditOutcome = {
    OK: 'OK',
    FAILED: 'FAILED',
} as const

export type StorageAuditOutcomeName = (typeof StorageAuditOutcome)[keyof typeof StorageAuditOutcome]

export interface ProbeResult {
    healthy: boolean
    error: string | null
    checkedAt: string
}

export interface AuditEntry {
    id: number
    ts: string
    actorAccountId: number | null
    actorMemberId: number | null
    systemActor: string | null
    stationId: number | null
    action: StorageAuditActionName
    oldConfig: string | null
    newConfig: string | null
    outcome: StorageAuditOutcomeName
    error: string | null
}

export interface S3Summary {
    type: 'S3'
    endpoint: string
    region: string
    bucket: string
    pathStyle: boolean
    sseAlgorithm: string
    basePath: string
}

export interface SmbSummary {
    type: 'SMB'
    host: string
    port: number
    share: string
    domain?: string
    basePath: string
    seal: boolean
    dfs: boolean
}

export interface SftpSummary {
    type: 'SFTP'
    host: string
    port: number
    username: string
    knownHostsPinned: boolean
    basePath: string
}

export type BackendOverrideSummary = S3Summary | SmbSummary | SftpSummary

export interface BackendOverrideResponse {
    instanceDefault: StorageBackendTypeName
    override: BackendOverrideSummary | null
}

export interface S3Request {
    type: 'S3'
    endpoint: string
    region: string
    bucket: string
    pathStyle: boolean
    sseAlgorithm: string
    basePath: string
    accessKey: string
    secretKey: string
}

export interface SmbRequest {
    type: 'SMB'
    host: string
    port: number
    share: string
    domain: string
    basePath: string
    seal: boolean
    dfs: boolean
    username: string
    password: string
}

export interface SftpRequest {
    type: 'SFTP'
    host: string
    port: number
    username: string
    knownHostsFingerprint: string
    basePath: string
    password: string
    privateKey: string
}

export type StationBackendRequest = S3Request | SmbRequest | SftpRequest

export interface InstanceBackendLocalRequest {
    type: 'LOCAL'
    root: string
}

export type InstanceBackendRequest = InstanceBackendLocalRequest | S3Request | SmbRequest | SftpRequest

export interface InstanceLocalSummary {
    type: 'LOCAL'
    root: string
}

export interface InstanceSmbSummary {
    type: 'SMB'
    host: string
    port: number
    share: string
    basePath: string
    seal: boolean
    dfs: boolean
}

export interface InstanceSftpSummary {
    type: 'SFTP'
    host: string
    port: number
    username: string
    basePath: string
    knownHostsPinned: boolean
}

export interface InstanceS3Summary {
    type: 'S3'
    endpoint: string
    region: string
    bucket: string
    pathStyle: boolean
    sseAlgorithm: string
    basePath: string
}

export type InstanceBackendSummary =
    | InstanceLocalSummary
    | InstanceSmbSummary
    | InstanceSftpSummary
    | InstanceS3Summary

export interface MigrationResultResponse {
    totalKeys: number
    copied: number
    skipped: number
    deleted: number
    copiedBytes: number
}

export interface InstanceMigrationStatusResponse {
    migrationInFlight: boolean
}

export interface InstanceMigrateRequest {
    target: InstanceBackendRequest
    keepSource: boolean
}

export async function getStationBackend(): Promise<BackendOverrideResponse> {
    const {data} = await client.get<BackendOverrideResponse>('/station/storage/backend')
    return data
}

export async function upsertStationBackend(request: StationBackendRequest): Promise<void> {
    await client.put('/station/storage/backend', request)
}

export async function deleteStationBackend(): Promise<void> {
    await client.delete('/station/storage/backend')
}

export async function probeStationBackend(request: StationBackendRequest): Promise<ProbeResult> {
    const {data} = await client.post<ProbeResult>('/station/storage/backend/probe', request)
    return data
}

export async function migrateStationBackend(request: StationBackendRequest): Promise<MigrationResultResponse> {
    const {data} = await client.post<MigrationResultResponse>('/station/storage/migrate', request)
    return data
}

export async function getStationStorageAudit(before?: string, limit = 50): Promise<AuditEntry[]> {
    const params: Record<string, string | number> = {limit}
    if (before) params.before = before
    const {data} = await client.get<AuditEntry[]>('/station/storage/audit', {params})
    return data
}

export async function getInstanceBackend(): Promise<InstanceBackendSummary> {
    const {data} = await client.get<InstanceBackendSummary>('/admin/storage/backend')
    return data
}

export async function updateInstanceBackend(request: InstanceBackendRequest): Promise<void> {
    await client.put('/admin/storage/backend', request)
}

export async function probeInstanceBackend(): Promise<ProbeResult> {
    const {data} = await client.post<ProbeResult>('/admin/storage/backend/probe')
    return data
}

export async function migrateInstanceBackend(request: InstanceMigrateRequest): Promise<MigrationResultResponse> {
    const {data} = await client.post<MigrationResultResponse>('/admin/storage/migrate', request)
    return data
}

export async function getInstanceMigrationStatus(): Promise<InstanceMigrationStatusResponse> {
    const {data} = await client.get<InstanceMigrationStatusResponse>('/admin/storage/migrate/status')
    return data
}

export async function getInstanceStorageAudit(
    options: {before?: string; stationUid?: string; limit?: number} = {},
): Promise<AuditEntry[]> {
    const params: Record<string, string | number> = {limit: options.limit ?? 50}
    if (options.before) params.before = options.before
    if (options.stationUid) params.stationUid = options.stationUid
    const {data} = await client.get<AuditEntry[]>('/admin/storage/audit', {params})
    return data
}
