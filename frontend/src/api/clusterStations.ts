/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    AvailableClusterResponse,
    ClusterApplicationResponse,
    ClusterApplicationView,
    ClusterStationResponse,
    components,
    StationClusterResponse,
} from './generated/schema'

export type ClusterApplicationStatusName = components['schemas']['ClusterApplicationStatus']

/** Where a station's request to join a cluster stands. */
export const ClusterApplicationStatus = {
    PENDING: 'PENDING',
    APPROVED: 'APPROVED',
    DENIED: 'DENIED',
    WITHDRAWN: 'WITHDRAWN',
} as const satisfies Record<ClusterApplicationStatusName, ClusterApplicationStatusName>

export async function listStations(): Promise<ClusterStationResponse[]> {
    const res = await client.get<ClusterStationResponse[]>('/cluster/stations')
    return res.data
}

export async function createStation(name: string): Promise<ClusterStationResponse> {
    const res = await client.post<ClusterStationResponse>('/cluster/stations', {name})
    return res.data
}

export async function releaseStation(stationUid: string): Promise<void> {
    await client.delete(`/cluster/stations/${stationUid}`)
}

/** The applications as the cluster sees them: which station is asking. */
export async function listApplications(): Promise<ClusterApplicationResponse[]> {
    const res = await client.get<ClusterApplicationResponse[]>('/cluster/applications')
    return res.data
}

export async function decideApplication(id: number, approve: boolean, reason?: string): Promise<void> {
    await client.put(`/cluster/applications/${id}`, {approve, reason: reason ?? null})
}

export async function getStationCluster(): Promise<StationClusterResponse> {
    const res = await client.get<StationClusterResponse>('/station/cluster')
    return res.data
}

export async function listAvailableClusters(): Promise<AvailableClusterResponse[]> {
    const res = await client.get<AvailableClusterResponse[]>('/station/cluster/available')
    return res.data
}

/** Asks a cluster to take the station in, answered with the application as the station sees it. */
export async function applyToCluster(clusterUid: string): Promise<ClusterApplicationView> {
    const res = await client.post<ClusterApplicationView>('/station/cluster/applications', {clusterUid})
    return res.data
}

export async function withdrawApplication(id: number): Promise<void> {
    await client.delete(`/station/cluster/applications/${id}`)
}
