/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface ClusterDeniedModules {
    deniedModules: string[]
}

export interface ClusterLookAndFeel {
    defaultTheme?: string | null
    customThemeColors?: string | null
    defaultFeel?: string | null
    themeLocked: boolean
    colorsLocked: boolean
    feelLocked: boolean
    logoLocked: boolean
}

export interface ClusterStationQuota {
    stationUid: string
    stationName: string
    /** What the station may use, or null when it falls back to the instance default. */
    quotaBytes?: number | null
}

export interface ClusterStoragePool {
    /** The whole the cluster may hand out, or null when the instance set no cap. */
    poolBytes?: number | null
    handedOut: number
    stations: ClusterStationQuota[]
}

export async function getDeniedModules(): Promise<ClusterDeniedModules> {
    const res = await client.get<ClusterDeniedModules>('/cluster/modules')
    return res.data
}

export async function setDeniedModules(deniedModules: string[]): Promise<void> {
    await client.put('/cluster/modules', {deniedModules})
}

export async function getLookAndFeel(): Promise<ClusterLookAndFeel> {
    const res = await client.get<ClusterLookAndFeel>('/cluster/look-and-feel')
    return res.data
}

export async function setLookAndFeel(data: ClusterLookAndFeel): Promise<void> {
    await client.put('/cluster/look-and-feel', data)
}

export async function getStoragePool(): Promise<ClusterStoragePool> {
    const res = await client.get<ClusterStoragePool>('/cluster/storage')
    return res.data
}

export async function setStationQuota(stationUid: string, quotaBytes: number | null): Promise<void> {
    await client.put(`/cluster/storage/stations/${stationUid}`, {quotaBytes})
}

/** Only an instance administrator can grant the pool itself. */
export async function setStoragePool(clusterUid: string, quotaBytes: number | null): Promise<void> {
    await client.put(`/clusters/${clusterUid}/storage-pool`, {quotaBytes})
}
