/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {MapTileProvider} from '@/composables/useMapsConfig'

export type GeocodingProvider = 'NONE' | 'NOMINATIM' | 'LOCATIONIQ' | 'GEOAPIFY'

export interface MapsTilesConfig {
    provider: MapTileProvider
    apiKey: string
    urlTemplate: string
    attribution: string
    minZoom: number
    maxZoom: number
}

export interface MapsGeocodingConfig {
    provider: GeocodingProvider
    apiKey: string
    contactEmail: string
}

export interface AdminMapsConfig {
    tiles: MapsTilesConfig
    geocoding: MapsGeocodingConfig
    tileCacheMaxMb: number
}

export interface TileCacheStats {
    bytes: number
    tiles: number
    maxBytes: number
}

export interface TestTileResult {
    url: string
    status: number
}

export async function getAdminMapsConfig(): Promise<AdminMapsConfig> {
    const res = await client.get<AdminMapsConfig>('/admin/settings/maps')
    return res.data
}

export async function updateAdminMapsConfig(config: AdminMapsConfig): Promise<AdminMapsConfig> {
    const res = await client.put<AdminMapsConfig>('/admin/settings/maps', config)
    return res.data
}

export async function testTile(z = 10, x = 536, y = 355): Promise<TestTileResult> {
    const res = await client.get<TestTileResult>('/admin/maps/test-tile', {params: {z, x, y}})
    return res.data
}

export async function getCacheStats(): Promise<TileCacheStats> {
    const res = await client.get<TileCacheStats>('/admin/maps/cache/stats')
    return res.data
}

export async function purgeCache(): Promise<TileCacheStats> {
    const res = await client.post<TileCacheStats>('/admin/maps/cache/purge')
    return res.data
}
