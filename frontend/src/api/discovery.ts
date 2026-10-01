/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    BlocklistResponse,
    ChangedCountResponse,
    components,
    DiscoveredStationResponse,
    DiscoveryEntry,
    DiscoveryInfoResponse,
    DiscoverNowResponse,
    DiscoverySettingsRequest,
    DiscoverySettingsResponse,
    IdentityResponse,
    InviteCodeResponse,
    PeerResponse,
    PublicStationInfo,
} from './generated/schema'

type Schemas = components['schemas']

/** Whether the entry is a station of another instance rather than one of this instance. */
export function isRemoteEntry(entry: DiscoveryEntry): boolean {
    return !!entry.instanceHost
}

export async function getPublicStationInfo(stationUid: string): Promise<PublicStationInfo> {
    const res = await client.get<PublicStationInfo>(`/public/station/${stationUid}/info`)
    return res.data
}

export async function listDiscoverable(): Promise<DiscoveryEntry[]> {
    const res = await client.get<DiscoveryEntry[]>('/public/discovery')
    return res.data
}

export async function requestFederation(stationUid: string): Promise<void> {
    await client.post('/discovery/request', {stationUid})
}

export async function generateInvite(stationUid: string): Promise<string> {
    const res = await client.post<InviteCodeResponse>('/public/discovery/invite', {stationUid})
    return res.data.inviteCode
}

export type DiscoveryPeerSource = Schemas['PeerSource']

export type BlocklistKind = Schemas['BlocklistKind']

export async function getDiscoveryIdentity(): Promise<IdentityResponse> {
    const res = await client.get<IdentityResponse>('/admin/discovery/identity')
    return res.data
}

export async function getDiscoverySettings(): Promise<DiscoverySettingsResponse> {
    const res = await client.get<DiscoverySettingsResponse>('/admin/discovery/settings')
    return res.data
}

export async function updateDiscoverySettings(update: DiscoverySettingsRequest): Promise<DiscoverySettingsResponse> {
    const res = await client.put<DiscoverySettingsResponse>('/admin/discovery/settings', update)
    return res.data
}

export async function listDiscoveryPeers(): Promise<PeerResponse[]> {
    const res = await client.get<PeerResponse[]>('/admin/discovery/peers')
    return res.data
}

export async function probeDiscoveryPeer(baseUrl: string): Promise<DiscoveryInfoResponse> {
    const res = await client.post<DiscoveryInfoResponse>('/admin/discovery/peers/probe', {baseUrl})
    return res.data
}

export async function addDiscoveryPeer(baseUrl: string, expectedPublicKey?: string): Promise<PeerResponse> {
    const res = await client.post<PeerResponse>('/admin/discovery/peers', {baseUrl, expectedPublicKey})
    return res.data
}

export async function deleteDiscoveryPeer(publicKey: string): Promise<void> {
    await client.delete(`/admin/discovery/peers/${encodeURIComponent(publicKey)}`)
}

export async function upvoteDiscoveryPeer(publicKey: string): Promise<PeerResponse> {
    const res = await client.post<PeerResponse>(`/admin/discovery/peers/${encodeURIComponent(publicKey)}/upvote`)
    return res.data
}

export async function downvoteDiscoveryPeer(publicKey: string): Promise<PeerResponse> {
    const res = await client.post<PeerResponse>(`/admin/discovery/peers/${encodeURIComponent(publicKey)}/downvote`)
    return res.data
}

export async function blockDiscoveryPeer(publicKey: string): Promise<PeerResponse> {
    const res = await client.post<PeerResponse>(`/admin/discovery/peers/${encodeURIComponent(publicKey)}/block`)
    return res.data
}

export async function unblockDiscoveryPeer(publicKey: string): Promise<PeerResponse> {
    const res = await client.post<PeerResponse>(`/admin/discovery/peers/${encodeURIComponent(publicKey)}/unblock`)
    return res.data
}

export async function pingDiscoveryPeerNow(publicKey: string): Promise<void> {
    await client.post(`/admin/discovery/peers/${encodeURIComponent(publicKey)}/ping`)
}

export async function discoverNow(): Promise<DiscoverNowResponse> {
    const res = await client.post<DiscoverNowResponse>('/admin/discovery/discover-now')
    return res.data
}

export async function seedFromFederation(): Promise<number> {
    const res = await client.post<ChangedCountResponse>('/admin/discovery/seed')
    return res.data.changed
}

export async function listDiscoveryBlocklist(): Promise<BlocklistResponse[]> {
    const res = await client.get<BlocklistResponse[]>('/admin/discovery/blocklist')
    return res.data
}

export async function addToBlocklist(value: string, kind: BlocklistKind, note?: string): Promise<void> {
    await client.post('/admin/discovery/blocklist', {value, kind, note})
}

export async function removeFromBlocklist(value: string): Promise<void> {
    await client.delete(`/admin/discovery/blocklist/${encodeURIComponent(value)}`)
}

export async function listDiscoveredStations(): Promise<DiscoveredStationResponse[]> {
    const res = await client.get<DiscoveredStationResponse[]>('/discovery/stations')
    return res.data
}
