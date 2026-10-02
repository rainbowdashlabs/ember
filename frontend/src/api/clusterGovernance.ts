/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    DeniedModulesResponse,
    EntryAudience,
    LookAndFeelRequest,
    PublicKbResponse,
    WikiAudienceRequest,
} from './generated/schema'

/**
 * What the association switches off, for one group of its stations or for all of them.
 *
 * <p>Leaving the group out asks about the denials that reach every station, which is what the screen
 * shows until somebody picks a tab. Denials add up: a station loses a module when the association
 * denies it outright or denies it for a group that station is in.
 */
export async function getDeniedModules(stationGroupId?: number | null): Promise<DeniedModulesResponse> {
    const res = await client.get<DeniedModulesResponse>('/cluster/modules', {
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
export async function getPublicKb(): Promise<PublicKbResponse> {
    const res = await client.get<PublicKbResponse>('/cluster/knowledge/public')
    return res.data
}

export async function setPublicKb(mode: string): Promise<void> {
    await client.put('/cluster/knowledge/public', {mode})
}

/** Which stations each entry of the association's wiki is for. */
export async function getWikiAudiences(): Promise<EntryAudience[]> {
    const res = await client.get<EntryAudience[]>('/cluster/knowledge/audiences')
    return res.data
}

export async function setWikiAudience(entry: WikiAudienceRequest): Promise<void> {
    await client.put('/cluster/knowledge/audiences', entry)
}

export async function getLookAndFeel(): Promise<LookAndFeelRequest> {
    const res = await client.get<LookAndFeelRequest>('/cluster/look-and-feel')
    return res.data
}

export async function setLookAndFeel(data: LookAndFeelRequest): Promise<void> {
    await client.put('/cluster/look-and-feel', data)
}

/** Only an instance administrator can grant the pool itself. */
export async function setStoragePool(clusterUid: string, quotaBytes: number | null): Promise<void> {
    await client.put(`/clusters/${clusterUid}/storage-pool`, {quotaBytes})
}
