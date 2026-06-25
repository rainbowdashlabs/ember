/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface CategoryUsage {
    category: string
    totalBytes: number
    fileCount: number
}

export interface StationUsageResponse {
    categories: CategoryUsage[]
    totalBytes: number
    quotaBytes: number
    quotaUsedPercent: number
    categoryQuotas: Record<string, number>
    usesOwnBackend: boolean
}

export interface AdminStationUsage {
    stationId: string
    stationName: string
    totalBytes: number
    quotaBytes: number
    quotaUsedPercent: number
    categories: CategoryUsage[]
    presetId: number | null
    presetName: string | null
    usesOwnBackend: boolean
}

export interface StorageQuotaPreset {
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

export interface PresetRequest {
    name: string
    total: number
    kb: number
    board: number
    images: number
    pages: number
    perFile: number
    perImage: number
}

export interface QuotaUpdateRequest {
    totalBytes: number | null
    kbBytes: number | null
    boardBytes: number | null
    imagesBytes: number | null
    pagesBytes: number | null
    perFileBytes: number | null
    perImageBytes: number | null
}

// Station-level usage
export async function getStationUsage(): Promise<StationUsageResponse> {
    const {data} = await client.get<StationUsageResponse>('/storage/usage')
    return data
}

// Admin: all stations usage
export async function getAdminUsage(): Promise<AdminStationUsage[]> {
    const {data} = await client.get<AdminStationUsage[]>('/admin/storage/usage')
    return data
}

// Admin: reconciliation
export async function recalculateAll(): Promise<void> {
    await client.post('/admin/storage/recalculate')
}

export async function recalculateStation(stationUid: string): Promise<void> {
    await client.post(`/admin/storage/recalculate/${stationUid}`)
}

// Admin: presets CRUD
export async function getPresets(): Promise<StorageQuotaPreset[]> {
    const {data} = await client.get<StorageQuotaPreset[]>('/admin/storage/presets')
    return data
}

export async function createPreset(preset: PresetRequest): Promise<StorageQuotaPreset> {
    const {data} = await client.post<StorageQuotaPreset>('/admin/storage/presets', preset)
    return data
}

export async function updatePreset(id: number, preset: PresetRequest): Promise<StorageQuotaPreset> {
    const {data} = await client.put<StorageQuotaPreset>(`/admin/storage/presets/${id}`, preset)
    return data
}

export async function deletePreset(id: number): Promise<void> {
    await client.delete(`/admin/storage/presets/${id}`)
}

export async function applyPreset(id: number, stationUids: string[]): Promise<void> {
    await client.post(`/admin/storage/presets/${id}/apply`, {stationUids})
}

// Admin: station quota management
export async function updateStationQuotas(stationUid: string, quotas: QuotaUpdateRequest): Promise<void> {
    await client.put(`/admin/storage/stations/${stationUid}/quotas`, quotas)
}

export async function resetStationQuotas(stationUid: string): Promise<void> {
    await client.delete(`/admin/storage/stations/${stationUid}/quotas`)
}

export const StorageCategory = {
    PAGE_FILES: 'PAGE_FILES',
    PAGE_IMAGES: 'PAGE_IMAGES',
    KB_FILES: 'KB_FILES',
    BOARD_ATTACHMENTS: 'BOARD_ATTACHMENTS',
    IMAGE_AVATAR: 'IMAGE_AVATAR',
    IMAGE_LOST_AND_FOUND: 'IMAGE_LOST_AND_FOUND',
    IMAGE_LOGO_FRAGMENT: 'IMAGE_LOGO_FRAGMENT',
    IMAGE_QUIZ_QUESTION: 'IMAGE_QUIZ_QUESTION',
    IMAGE_KB_ICON: 'IMAGE_KB_ICON',
    IMAGE_KB_IMAGE: 'IMAGE_KB_IMAGE',
    DOCUMENT: 'DOCUMENT',
    DISCOVERY_KEY: 'DISCOVERY_KEY',
    MAP_TILE_CACHE: 'MAP_TILE_CACHE',
    DEMO_AVATAR: 'DEMO_AVATAR',
} as const

export type StorageCategoryName = (typeof StorageCategory)[keyof typeof StorageCategory]
