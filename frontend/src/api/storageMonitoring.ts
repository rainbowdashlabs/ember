/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import {
    QuotaOrigin,
    type AdminStationUsage,
    type PresetRequest,
    type QuotaUpdateRequest,
    type StationUsageResponse,
    type StorageQuotaPreset,
} from './generated/schema'

/**
 * Whether the number on a row was decided by an association rather than by the instance.
 *
 * <p>A quota is resolved in this order: what an association granted the station, what it gives its
 * stations by default, what an instance administrator set for it, and last the instance configuration.
 * A station under an association never reaches the third rung, because the instance's lever there is
 * the pool it granted.
 */
export function isClusterOrigin(origin?: QuotaOrigin | null): boolean {
    return origin === QuotaOrigin.CLUSTER_GRANT || origin === QuotaOrigin.CLUSTER_DEFAULT
}

export async function getStationUsage(): Promise<StationUsageResponse> {
    const {data} = await client.get<StationUsageResponse>('/storage/usage')
    return data
}

export async function getAdminUsage(): Promise<AdminStationUsage[]> {
    const {data} = await client.get<AdminStationUsage[]>('/admin/storage/usage')
    return data
}

export async function recalculateAll(): Promise<void> {
    await client.post('/admin/storage/recalculate')
}

export async function recalculateStation(stationUid: string): Promise<void> {
    await client.post(`/admin/storage/recalculate/${stationUid}`)
}

const presets = createCrudResource<StorageQuotaPreset, PresetRequest>('/admin/storage/presets')

export const getPresets = presets.list
export const createPreset = presets.create
export const updatePreset = presets.update
export const deletePreset = presets.remove

export async function applyPreset(id: number, stationUids: string[]): Promise<void> {
    await client.post(`/admin/storage/presets/${id}/apply`, {stationUids})
}

export async function updateStationQuotas(stationUid: string, quotas: QuotaUpdateRequest): Promise<void> {
    await client.put(`/admin/storage/stations/${stationUid}/quotas`, quotas)
}

export async function resetStationQuotas(stationUid: string): Promise<void> {
    await client.delete(`/admin/storage/stations/${stationUid}/quotas`)
}
