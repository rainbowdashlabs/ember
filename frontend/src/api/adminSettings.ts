/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface ApplicationSettings {
    stationRegistrationEnabled: boolean
}

export interface RegistrationStatus {
    enabled: boolean
}

export interface AuthConfig {
    tokenBytes: number
    verifyTokenHours: number
    passwordTokenHours: number
    sessionMinutes: number
}

export interface MailingConfig {
    provider: string
    senderAddress: string
    senderName: string
    user: string
    password: string
    apiKey: string
    smtpHost: string
    smtpPort: number
    smtpSsl: boolean
    dailySendLimit: number
    notificationDigestIntervalMinutes: number
}

export interface LegalDocument {
    type: string
    content: string
    version: string
}

export async function getSettings(): Promise<ApplicationSettings> {
    const res = await client.get<ApplicationSettings>('/admin/settings')
    return res.data
}

export async function updateSettings(settings: ApplicationSettings): Promise<ApplicationSettings> {
    const res = await client.put<ApplicationSettings>('/admin/settings', settings)
    return res.data
}

export async function isRegistrationEnabled(): Promise<boolean> {
    const res = await client.get<RegistrationStatus>('/public/settings/station-registration')
    return res.data.enabled
}

export async function getAuthConfig(): Promise<AuthConfig> {
    const res = await client.get<AuthConfig>('/admin/config/auth')
    return res.data
}

export async function updateAuthConfig(data: AuthConfig): Promise<AuthConfig> {
    const res = await client.put<AuthConfig>('/admin/config/auth', data)
    return res.data
}

export async function getMailingConfig(): Promise<MailingConfig> {
    const res = await client.get<MailingConfig>('/admin/config/mailing')
    return res.data
}

export async function updateMailingConfig(data: MailingConfig): Promise<MailingConfig> {
    const res = await client.put<MailingConfig>('/admin/config/mailing', data)
    return res.data
}

export async function getLegalDocument(type: string): Promise<LegalDocument> {
    const res = await client.get<LegalDocument>(`/admin/legal/${type}`)
    return res.data
}

export async function updateLegalDocument(type: string, content: string): Promise<LegalDocument> {
    const res = await client.put<LegalDocument>(`/admin/legal/${type}`, { content })
    return res.data
}
