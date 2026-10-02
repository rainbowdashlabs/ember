/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {queryParams} from './crud'
import type {
    AuditEntryResponse,
    BackendRequest,
    components,
    MigrationResponse,
    PlacementResponse,
    PolicyRequest,
    PolicyResponse,
    ProbeResult,
} from './generated/schema'

type Schemas = components['schemas']

export type StoragePlacementActualName = Schemas['Actual']

/** Where a station's files are. */
export const StoragePlacementActual = {
    ITS_OWN: 'ITS_OWN',
    THE_CLUSTERS: 'THE_CLUSTERS',
    INSTANCE_DEFAULT: 'INSTANCE_DEFAULT',
} as const satisfies Record<StoragePlacementActualName, StoragePlacementActualName>

export type StoragePlacementExpectedName = Schemas['Expected']

/** Where a station's files belong, given what its association decided. */
export const StoragePlacementExpected = {
    ITS_OWN: 'ITS_OWN',
    THE_CLUSTERS: 'THE_CLUSTERS',
    INSTANCE_DEFAULT: 'INSTANCE_DEFAULT',
    WHEREVER_IT_IS: 'WHEREVER_IT_IS',
} as const satisfies Record<StoragePlacementExpectedName, StoragePlacementExpectedName>

/** What the association decided, and the storage it is standing on with nothing secret in it. */
export async function getClusterBackend(): Promise<PolicyResponse> {
    const {data} = await client.get<PolicyResponse>('/cluster/storage/backend')
    return data
}

export async function setClusterBackendPolicy(request: PolicyRequest): Promise<void> {
    await client.put('/cluster/storage/backend/policy', request)
}

export async function probeClusterBackend(): Promise<ProbeResult> {
    const {data} = await client.post<ProbeResult>('/cluster/storage/backend/probe')
    return data
}

export async function probeClusterBackendConfig(request: BackendRequest): Promise<ProbeResult> {
    const {data} = await client.post<ProbeResult>('/cluster/storage/backend/probe-config', request)
    return data
}

export async function applyClusterBackend(request: BackendRequest): Promise<PolicyResponse> {
    const {data} = await client.post<PolicyResponse>('/cluster/storage/backend/apply', request)
    return data
}

export async function dropClusterBackend(): Promise<void> {
    await client.delete('/cluster/storage/backend')
}

/** Every station of the association, where its files are and where they belong. */
export async function getClusterPlacements(): Promise<PlacementResponse[]> {
    const {data} = await client.get<PlacementResponse[]>('/cluster/storage/backend/placements')
    return data
}

/**
 * The association's storage history: its own storage, what it decided about it, and the moves it made for
 * its stations.
 */
export async function getClusterStorageAudit(before?: string, limit = 50): Promise<AuditEntryResponse[]> {
    const {data} = await client.get<AuditEntryResponse[]>('/cluster/storage/audit', {
        params: queryParams({limit, before: before || undefined}),
    })
    return data
}

export async function moveStationStorage(stationUid: string): Promise<MigrationResponse> {
    const {data} = await client.post<MigrationResponse>(
        `/cluster/storage/backend/placements/${stationUid}/move`,
    )
    return data
}
