/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {SettingsRequest, SettingsResponse} from './generated/schema'

export async function getSettings(): Promise<SettingsResponse> {
    const res = await client.get<SettingsResponse>('/settings')
    return res.data
}

export async function updateSettings(data: SettingsRequest): Promise<SettingsResponse> {
    const res = await client.put<SettingsResponse>('/settings', data)
    return res.data
}
