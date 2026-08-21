/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** Where a station's request to join a cluster stands. */
export const ClusterApplicationStatus = {
    PENDING: 'PENDING',
    APPROVED: 'APPROVED',
    DENIED: 'DENIED',
    WITHDRAWN: 'WITHDRAWN',
} as const

export type ClusterApplicationStatusName =
    (typeof ClusterApplicationStatus)[keyof typeof ClusterApplicationStatus]

export interface ClusterStation {
    uid: string
    name: string
    publicSlug?: string | null
}

/** An application as the cluster sees it: which station is asking. */
export interface ClusterApplication {
    id: number
    stationName?: string | null
    requestedAt: string
    status: ClusterApplicationStatusName
    denyReason?: string | null
    resolvedAt?: string | null
}

/** The same row as the station sees it: which cluster was asked. */
export interface StationClusterApplication {
    id: number
    clusterName?: string | null
    requestedAt: string
    status: ClusterApplicationStatusName
    denyReason?: string | null
    resolvedAt?: string | null
}

export interface StationCluster {
    clusterUid?: string | null
    clusterName?: string | null
    clusterDescription?: string | null
    applications: StationClusterApplication[]
}

export interface AvailableCluster {
    uid: string
    name: string
    description?: string | null
}

// -- The cluster's side --

export async function listStations(): Promise<ClusterStation[]> {
    const res = await client.get<ClusterStation[]>('/cluster/stations')
    return res.data
}

export async function createStation(name: string): Promise<ClusterStation> {
    const res = await client.post<ClusterStation>('/cluster/stations', {name})
    return res.data
}

export async function releaseStation(stationUid: string): Promise<void> {
    await client.delete(`/cluster/stations/${stationUid}`)
}

export async function listApplications(): Promise<ClusterApplication[]> {
    const res = await client.get<ClusterApplication[]>('/cluster/applications')
    return res.data
}

export async function decideApplication(id: number, approve: boolean, reason?: string): Promise<void> {
    await client.put(`/cluster/applications/${id}`, {approve, reason: reason ?? null})
}

// -- The station's side --

export async function getStationCluster(): Promise<StationCluster> {
    const res = await client.get<StationCluster>('/station/cluster')
    return res.data
}

export async function listAvailableClusters(): Promise<AvailableCluster[]> {
    const res = await client.get<AvailableCluster[]>('/station/cluster/available')
    return res.data
}

export async function applyToCluster(clusterUid: string): Promise<StationClusterApplication> {
    const res = await client.post<StationClusterApplication>('/station/cluster/applications', {clusterUid})
    return res.data
}

export async function withdrawApplication(id: number): Promise<void> {
    await client.delete(`/station/cluster/applications/${id}`)
}
