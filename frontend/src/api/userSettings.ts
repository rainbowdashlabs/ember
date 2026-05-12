/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { UserSettings, UserSettingsRequest } from './types'

export async function getSettings(): Promise<UserSettings> {
    const res = await client.get<UserSettings>('/settings')
    return res.data
}

export async function updateSettings(data: UserSettingsRequest): Promise<UserSettings> {
    const res = await client.put<UserSettings>('/settings', data)
    return res.data
}
