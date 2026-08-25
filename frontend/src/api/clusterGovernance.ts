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
