/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {AdminMapsConfig, CacheStats, TestTileResult} from './generated/schema'

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

export async function getCacheStats(): Promise<CacheStats> {
    const res = await client.get<CacheStats>('/admin/maps/cache/stats')
    return res.data
}

export async function purgeCache(): Promise<CacheStats> {
    const res = await client.post<CacheStats>('/admin/maps/cache/purge')
    return res.data
}
