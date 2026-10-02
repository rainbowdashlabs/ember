/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {DimensionsRequest, OverviewResponse, TierRequest, TierResponse} from './generated/schema'

/**
 * The association's picture of its room: the pool, its defaults, its tiers and every station with what
 * it was granted beside what it may therefore keep.
 */
export async function getOverview(): Promise<OverviewResponse> {
    const {data} = await client.get<OverviewResponse>('/cluster/storage')
    return data
}

/** A null dimension means the association is not deciding that one, and whatever stands behind it applies. */
export async function setDefaults(defaults: DimensionsRequest): Promise<void> {
    await client.put('/cluster/storage/defaults', defaults)
}

export async function listTiers(): Promise<TierResponse[]> {
    const {data} = await client.get<TierResponse[]>('/cluster/storage/presets')
    return data
}

export async function createTier(tier: TierRequest): Promise<TierResponse> {
    const {data} = await client.post<TierResponse>('/cluster/storage/presets', tier)
    return data
}

export async function updateTier(tierId: number, tier: TierRequest): Promise<void> {
    await client.put(`/cluster/storage/presets/${tierId}`, tier)
}

export async function deleteTier(tierId: number): Promise<void> {
    await client.delete(`/cluster/storage/presets/${tierId}`)
}

export async function applyTier(tierId: number, stationUids: string[]): Promise<void> {
    await client.post(`/cluster/storage/presets/${tierId}/apply`, {stationUids})
}

/** Grants one station room, in as many of the seven dimensions as the association cares about. */
export async function setStationRoom(stationUid: string, room: DimensionsRequest): Promise<void> {
    await client.put(`/cluster/storage/stations/${stationUid}`, room)
}

/** Takes the room back, so the station lives on the association's defaults again. */
export async function handBackStationRoom(stationUid: string): Promise<void> {
    await client.delete(`/cluster/storage/stations/${stationUid}`)
}
