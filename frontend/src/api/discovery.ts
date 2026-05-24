/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface DiscoveryEntry {
    stationUid: string
    name: string
    description: string | null
    hasLogo: boolean
    hasPublicKb: boolean
    alreadyFederated: boolean
}

export async function listDiscoverable(): Promise<DiscoveryEntry[]> {
    const res = await client.get<DiscoveryEntry[]>('/public/discovery')
    return res.data
}

export async function requestFederation(stationUid: string): Promise<void> {
    await client.post('/discovery/request', {stationUid})
}

export async function generateInvite(stationUid: string): Promise<string> {
    const res = await client.post<{ inviteCode: string }>('/public/discovery/invite', {stationUid})
    return res.data.inviteCode
}
