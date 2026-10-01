/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {
    AdminStationUsage,
    components,
    PresetRequest,
    QuotaUpdateRequest,
    StationUsageResponse,
    StorageQuotaPreset,
} from './generated/schema'

export type QuotaOriginName = components['schemas']['QuotaOrigin']

/**
 * Whose word a resolved quota is on.
 *
 * <p>Consulted in this order: what an association granted the station, what it gives its stations by
 * default, what an instance administrator set for it, and last the instance configuration. A station under
 * an association never reaches the third rung, because the instance's lever there is the pool it granted.
 */
export const QuotaOrigin = {
    CLUSTER_GRANT: 'CLUSTER_GRANT',
    CLUSTER_DEFAULT: 'CLUSTER_DEFAULT',
    INSTANCE_OVERRIDE: 'INSTANCE_OVERRIDE',
    INSTANCE_DEFAULT: 'INSTANCE_DEFAULT',
    UNLIMITED: 'UNLIMITED',
} as const satisfies Record<QuotaOriginName, QuotaOriginName>

/** Whether the number on a row was decided by an association rather than by the instance. */
export function isClusterOrigin(origin?: QuotaOriginName | null): boolean {
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

export const StorageCategory = {
    MEDIA_FILES: 'MEDIA_FILES',
    INSTANCE_MEDIA_FILES: 'INSTANCE_MEDIA_FILES',
    MEDIA_IMAGES: 'MEDIA_IMAGES',
    KB_FILES: 'KB_FILES',
    MEMBER_DOCUMENTS: 'MEMBER_DOCUMENTS',
    MOVEMENT_DOCUMENTS: 'MOVEMENT_DOCUMENTS',
    BOARD_ATTACHMENTS: 'BOARD_ATTACHMENTS',
    IMAGE_AVATAR: 'IMAGE_AVATAR',
    IMAGE_LOST_AND_FOUND: 'IMAGE_LOST_AND_FOUND',
    IMAGE_LOGO_FRAGMENT: 'IMAGE_LOGO_FRAGMENT',
    IMAGE_STATION_LOGO: 'IMAGE_STATION_LOGO',
    IMAGE_QUIZ_QUESTION: 'IMAGE_QUIZ_QUESTION',
    IMAGE_KB_ICON: 'IMAGE_KB_ICON',
    IMAGE_KB_IMAGE: 'IMAGE_KB_IMAGE',
    DOCUMENT: 'DOCUMENT',
    DISCOVERY_KEY: 'DISCOVERY_KEY',
    MAP_TILE_CACHE: 'MAP_TILE_CACHE',
    DEMO_AVATAR: 'DEMO_AVATAR',
} as const

export type StorageCategoryName = (typeof StorageCategory)[keyof typeof StorageCategory]
