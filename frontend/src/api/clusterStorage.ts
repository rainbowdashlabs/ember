/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {CategoryUsage, QuotaOriginName} from './storageMonitoring'

/**
 * The seven dimensions room is measured in.
 *
 * <p>A null means whoever sent it is not deciding that one, and whatever stands behind it applies.
 */
export interface QuotaDimensions {
    totalBytes: number | null
    kbBytes: number | null
    boardBytes: number | null
    imagesBytes: number | null
    pagesBytes: number | null
    perFileBytes: number | null
    perImageBytes: number | null
}

export interface ResolvedValue {
    bytes: number
    origin: QuotaOriginName
}

export interface ResolvedQuotas {
    total: ResolvedValue
    kb: ResolvedValue
    board: ResolvedValue
    images: ResolvedValue
    pages: ResolvedValue
    perFile: ResolvedValue
    perImage: ResolvedValue
}

/** A tier the association keeps, the same seven dimensions the instance's own tiers carry. */
export interface ClusterQuotaTier {
    id: number
    name: string
    total: number
    kb: number
    board: number
    images: number
    pages: number
    perFile: number
    perImage: number
}

export type ClusterQuotaTierRequest = Omit<ClusterQuotaTier, 'id'>

/**
 * One station in the association's picture of its room.
 *
 * <p>Granted and resolved side by side because they answer different questions: what this association
 * decided, and what the station may therefore keep.
 */
export interface ClusterStationRoom {
    stationUid: string
    stationName: string
    quotaBytes: number | null
    ownStore: boolean
    granted: QuotaDimensions
    resolved: ResolvedQuotas
    usedBytes: number
    usage: CategoryUsage[]
    presetId: number | null
    presetName: string | null
}

export interface ClusterStorageOverview {
    poolBytes: number | null
    handedOut: number
    defaults: QuotaDimensions
    presets: ClusterQuotaTier[]
    stations: ClusterStationRoom[]
}

export async function getOverview(): Promise<ClusterStorageOverview> {
    const {data} = await client.get<ClusterStorageOverview>('/cluster/storage')
    return data
}

export async function setDefaults(defaults: QuotaDimensions): Promise<void> {
    await client.put('/cluster/storage/defaults', defaults)
}

export async function listTiers(): Promise<ClusterQuotaTier[]> {
    const {data} = await client.get<ClusterQuotaTier[]>('/cluster/storage/presets')
    return data
}

export async function createTier(tier: ClusterQuotaTierRequest): Promise<ClusterQuotaTier> {
    const {data} = await client.post<ClusterQuotaTier>('/cluster/storage/presets', tier)
    return data
}

export async function updateTier(tierId: number, tier: ClusterQuotaTierRequest): Promise<void> {
    await client.put(`/cluster/storage/presets/${tierId}`, tier)
}

export async function deleteTier(tierId: number): Promise<void> {
    await client.delete(`/cluster/storage/presets/${tierId}`)
}

export async function applyTier(tierId: number, stationUids: string[]): Promise<void> {
    await client.post(`/cluster/storage/presets/${tierId}/apply`, {stationUids})
}

/** Grants one station room, in as many of the seven dimensions as the association cares about. */
export async function setStationRoom(stationUid: string, room: QuotaDimensions): Promise<void> {
    await client.put(`/cluster/storage/stations/${stationUid}`, room)
}

/** Takes the room back, so the station lives on the association's defaults again. */
export async function handBackStationRoom(stationUid: string): Promise<void> {
    await client.delete(`/cluster/storage/stations/${stationUid}`)
}
