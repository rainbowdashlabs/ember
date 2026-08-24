/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    BackendOverrideSummary,
    MigrationResultResponse,
    ProbeResult,
    StationBackendRequest,
} from './storageBackend'

/**
 * How far an association's own storage reaches.
 *
 * What it decided, not where anything is: a station moves when somebody moves it, and until then a station
 * under {@code EVERY_STATION} is out of place rather than relocated.
 */
export const ClusterBackendReach = {
    NONE: 'NONE',
    OWN_FILES: 'OWN_FILES',
    EVERY_STATION: 'EVERY_STATION',
} as const

export type ClusterBackendReachName = (typeof ClusterBackendReach)[keyof typeof ClusterBackendReach]

/** Where a station's files are. */
export const StoragePlacementActual = {
    ITS_OWN: 'ITS_OWN',
    THE_CLUSTERS: 'THE_CLUSTERS',
    INSTANCE_DEFAULT: 'INSTANCE_DEFAULT',
} as const

export type StoragePlacementActualName = (typeof StoragePlacementActual)[keyof typeof StoragePlacementActual]

/** Where a station's files belong, given what its association decided. */
export const StoragePlacementExpected = {
    ITS_OWN: 'ITS_OWN',
    THE_CLUSTERS: 'THE_CLUSTERS',
    INSTANCE_DEFAULT: 'INSTANCE_DEFAULT',
    WHEREVER_IT_IS: 'WHEREVER_IT_IS',
} as const

export type StoragePlacementExpectedName = (typeof StoragePlacementExpected)[keyof typeof StoragePlacementExpected]

/** What the association decided, and the storage it is standing on with nothing secret in it. */
export interface ClusterBackendPolicy {
    reach: ClusterBackendReachName
    locked: boolean
    backend: BackendOverrideSummary | null
}

/** What the association is deciding. */
export interface ClusterBackendPolicyRequest {
    reach: ClusterBackendReachName
    locked: boolean
}

/** One station of the association, where its files are and where they belong. */
export interface StoragePlacement {
    stationUid: string
    name: string
    homeStation: boolean
    actual: StoragePlacementActualName
    expected: StoragePlacementExpectedName
    inPlace: boolean
}

export async function getClusterBackend(): Promise<ClusterBackendPolicy> {
    const {data} = await client.get<ClusterBackendPolicy>('/cluster/storage/backend')
    return data
}

export async function setClusterBackendPolicy(request: ClusterBackendPolicyRequest): Promise<void> {
    await client.put('/cluster/storage/backend/policy', request)
}

export async function probeClusterBackend(): Promise<ProbeResult> {
    const {data} = await client.post<ProbeResult>('/cluster/storage/backend/probe')
    return data
}

export async function probeClusterBackendConfig(request: StationBackendRequest): Promise<ProbeResult> {
    const {data} = await client.post<ProbeResult>('/cluster/storage/backend/probe-config', request)
    return data
}

export async function applyClusterBackend(request: StationBackendRequest): Promise<ClusterBackendPolicy> {
    const {data} = await client.post<ClusterBackendPolicy>('/cluster/storage/backend/apply', request)
    return data
}

export async function dropClusterBackend(): Promise<void> {
    await client.delete('/cluster/storage/backend')
}

export async function getClusterPlacements(): Promise<StoragePlacement[]> {
    const {data} = await client.get<StoragePlacement[]>('/cluster/storage/backend/placements')
    return data
}

export async function moveStationStorage(stationUid: string): Promise<MigrationResultResponse> {
    const {data} = await client.post<MigrationResultResponse>(
        `/cluster/storage/backend/placements/${stationUid}/move`,
    )
    return data
}
