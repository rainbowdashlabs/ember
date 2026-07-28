/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
export interface NotificationToggle {
    app: boolean
    email: boolean
    feed: boolean
}

export interface UserSettings {
    emailEnabled: boolean
    theme: string
    darkMode: string
    notifications: Record<string, NotificationToggle>
    mailConfigured: boolean
    mailProviderName: string
    mailProviderUrl: string
}

export interface UserSettingsRequest {
    emailEnabled?: boolean
    theme?: string
    darkMode?: string
    feel?: string
    notifications?: Record<string, NotificationToggle>
}

export async function getSettings(): Promise<UserSettings> {
    const res = await client.get<UserSettings>('/settings')
    return res.data
}

export async function updateSettings(data: UserSettingsRequest): Promise<UserSettings> {
    const res = await client.put<UserSettings>('/settings', data)
    return res.data
}
