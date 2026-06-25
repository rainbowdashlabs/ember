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
    hasPublicCalendar: boolean
    alreadyFederated: boolean
    isOwnStation: boolean
    publicSlug: string | null
    city: string | null
    country: string | null
    latitude: number | null
    longitude: number | null
}

export interface PublicStationInfo {
    stationUid: string
    name: string
    description: string | null
    hasLogo: boolean
    hasPublicKb: boolean
    hasPublicCalendar: boolean
    hasPublicPages: boolean
    hasPublicWaitlist: boolean
    hasPublicBlog: boolean
    landingPageSlug: string | null
    publicSlug: string | null
    defaultTheme: string | null
    defaultFeel: string | null
    customThemeColors: string | null
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
    const res = await client.post<{ inviteCode: string }>('/public/discovery/invite', {stationUid})
    return res.data.inviteCode
}

// ---------------------------------------------------------------------------
// Discovery chain — cross-instance gossip + public station catalog.
// ---------------------------------------------------------------------------

export type DiscoveryPeerSource = 'BOOTSTRAP' | 'GOSSIP' | 'MANUAL'

export interface DiscoveryIdentity {
    instanceId: string
    publicKey: string
    baseUrl: string
}

export interface DiscoverySettings {
    enabled: boolean
    maxDepth: number
    pingIntervalMinutes: number
    hardMaxDepth: number
}

export interface DiscoverySettingsUpdate {
    enabled?: boolean
    maxDepth?: number
    pingIntervalMinutes?: number
}

export interface DiscoveryPeer {
    publicKey: string
    baseUrl: string
    instanceId: string
    firstSeenAt: string
    lastSeenAt: string
    lastPingedAt: string | null
    lastReachedAt: string | null
    reachable: boolean
    source: DiscoveryPeerSource
    introducedBy: string | null
    reputation: number
    blocked: boolean
}

export interface DiscoveryInfoProbe {
    baseUrl: string
    instanceId: string
    publicKey: string
    softwareVersion: string
    discoveryEnabled: boolean
}

export interface DiscoveryNowResult {
    pingsDispatched: number
    stationsFetched: number
}

export type BlocklistKind = 'BASE_URL' | 'PUBLIC_KEY'

export interface DiscoveryBlocklistEntry {
    value: string
    kind: BlocklistKind
    note: string | null
    createdAt: string
}

export interface DiscoveredStation {
    stationUid: string
    name: string
    slogan: string | null
    logoUrl: string | null
    country: string | null
    region: string | null
    city: string | null
    contactUrl: string | null
    tags: string[]
    memberCount: string
    publishedAt: string | null
    addressLine: string | null
    latitude: number | null
    longitude: number | null
    instancePublicKey: string
    fetchedAt: string
}

// -- Admin --

export async function getDiscoveryIdentity(): Promise<DiscoveryIdentity> {
    const res = await client.get<DiscoveryIdentity>('/admin/discovery/identity')
    return res.data
}

export async function getDiscoverySettings(): Promise<DiscoverySettings> {
    const res = await client.get<DiscoverySettings>('/admin/discovery/settings')
    return res.data
}

export async function updateDiscoverySettings(update: DiscoverySettingsUpdate): Promise<DiscoverySettings> {
    const res = await client.put<DiscoverySettings>('/admin/discovery/settings', update)
    return res.data
}

export async function listDiscoveryPeers(): Promise<DiscoveryPeer[]> {
    const res = await client.get<DiscoveryPeer[]>('/admin/discovery/peers')
    return res.data
}

export async function probeDiscoveryPeer(baseUrl: string): Promise<DiscoveryInfoProbe> {
    const res = await client.post<DiscoveryInfoProbe>('/admin/discovery/peers/probe', {baseUrl})
    return res.data
}

export async function addDiscoveryPeer(baseUrl: string, expectedPublicKey?: string): Promise<DiscoveryPeer> {
    const res = await client.post<DiscoveryPeer>('/admin/discovery/peers', {baseUrl, expectedPublicKey})
    return res.data
}

export async function deleteDiscoveryPeer(publicKey: string): Promise<void> {
    await client.delete(`/admin/discovery/peers/${encodeURIComponent(publicKey)}`)
}

export async function upvoteDiscoveryPeer(publicKey: string): Promise<DiscoveryPeer> {
    const res = await client.post<DiscoveryPeer>(`/admin/discovery/peers/${encodeURIComponent(publicKey)}/upvote`)
    return res.data
}

export async function downvoteDiscoveryPeer(publicKey: string): Promise<DiscoveryPeer> {
    const res = await client.post<DiscoveryPeer>(`/admin/discovery/peers/${encodeURIComponent(publicKey)}/downvote`)
    return res.data
}

export async function blockDiscoveryPeer(publicKey: string): Promise<DiscoveryPeer> {
    const res = await client.post<DiscoveryPeer>(`/admin/discovery/peers/${encodeURIComponent(publicKey)}/block`)
    return res.data
}

export async function unblockDiscoveryPeer(publicKey: string): Promise<DiscoveryPeer> {
    const res = await client.post<DiscoveryPeer>(`/admin/discovery/peers/${encodeURIComponent(publicKey)}/unblock`)
    return res.data
}

export async function pingDiscoveryPeerNow(publicKey: string): Promise<void> {
    await client.post(`/admin/discovery/peers/${encodeURIComponent(publicKey)}/ping`)
}

export async function discoverNow(): Promise<DiscoveryNowResult> {
    const res = await client.post<DiscoveryNowResult>('/admin/discovery/discover-now')
    return res.data
}

export async function seedFromFederation(): Promise<number> {
    const res = await client.post<{changed: number}>('/admin/discovery/seed')
    return res.data.changed
}

export async function listDiscoveryBlocklist(): Promise<DiscoveryBlocklistEntry[]> {
    const res = await client.get<DiscoveryBlocklistEntry[]>('/admin/discovery/blocklist')
    return res.data
}

export async function addToBlocklist(value: string, kind: BlocklistKind, note?: string): Promise<void> {
    await client.post('/admin/discovery/blocklist', {value, kind, note})
}

export async function removeFromBlocklist(value: string): Promise<void> {
    await client.delete(`/admin/discovery/blocklist/${encodeURIComponent(value)}`)
}

// -- Authenticated user-facing --

export async function listDiscoveredStations(): Promise<DiscoveredStation[]> {
    const res = await client.get<DiscoveredStation[]>('/discovery/stations')
    return res.data
}
