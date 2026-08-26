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

/**
 * What the association switches off, for one group of its stations or for all of them.
 *
 * <p>Leaving the group out asks about the denials that reach every station, which is what the screen
 * shows until somebody picks a tab. Denials add up: a station loses a module when the association
 * denies it outright or denies it for a group that station is in.
 */
export async function getDeniedModules(stationGroupId?: number | null): Promise<ClusterDeniedModules> {
    const res = await client.get<ClusterDeniedModules>('/cluster/modules', {
        params: stationGroupId == null ? {} : {stationGroupId},
    })
    return res.data
}

export async function setDeniedModules(
    deniedModules: string[],
    stationGroupId?: number | null,
): Promise<void> {
    await client.put('/cluster/modules', {deniedModules}, {
        params: stationGroupId == null ? {} : {stationGroupId},
    })
}

/** Whether the association's wiki stands on the public web, and the address it answers at. */
export interface ClusterPublicKb {
    mode: string
    stationUid: string
}

export async function getPublicKb(): Promise<ClusterPublicKb> {
    const res = await client.get<ClusterPublicKb>('/cluster/knowledge/public')
    return res.data
}

export async function setPublicKb(mode: string): Promise<void> {
    await client.put('/cluster/knowledge/public', {mode})
}

/** Which stations one entry of the association's wiki is for. */
export interface WikiAudience {
    id: number
    fileId: number | null
    folderId: number | null
    scope: string
    partnerIds: number[]
}

export async function getWikiAudiences(): Promise<WikiAudience[]> {
    const res = await client.get<WikiAudience[]>('/cluster/knowledge/audiences')
    return res.data
}

export async function setWikiAudience(
    entry: {fileId?: number; folderId?: number; everyStation: boolean; partnerIds: number[]},
): Promise<void> {
    await client.put('/cluster/knowledge/audiences', entry)
}

export async function getLookAndFeel(): Promise<ClusterLookAndFeel> {
    const res = await client.get<ClusterLookAndFeel>('/cluster/look-and-feel')
    return res.data
}

export async function setLookAndFeel(data: ClusterLookAndFeel): Promise<void> {
    await client.put('/cluster/look-and-feel', data)
}

/** Only an instance administrator can grant the pool itself. */
export async function setStoragePool(clusterUid: string, quotaBytes: number | null): Promise<void> {
    await client.put(`/clusters/${clusterUid}/storage-pool`, {quotaBytes})
}
