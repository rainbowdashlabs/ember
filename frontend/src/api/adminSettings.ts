/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface ApplicationSettings {
    stationRegistrationEnabled: boolean
    instanceDefaultTheme: string
    instanceDefaultFeel: string
    instanceLockFeel: boolean
    forcePrideFlag: boolean
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

export interface PublicTheme {
    defaultTheme: string
    defaultFeel: string
    lockFeel: boolean
    forcePrideFlag: boolean
}

export async function getPublicTheme(): Promise<PublicTheme> {
    const res = await client.get<PublicTheme>('/public/settings/theme')
    return res.data
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

export async function getLegalDocument(type: string, locale?: string): Promise<LegalDocument> {
    const path = locale ? `/admin/legal/${type}/${locale}` : `/admin/legal/${type}`
    const res = await client.get<LegalDocument>(path)
    return res.data
}

export async function getLegalLocales(type: string): Promise<string[]> {
    const res = await client.get<string[]>(`/admin/legal/${type}/locales`)
    return res.data
}

export async function updateLegalDocument(type: string, content: string, locale?: string): Promise<LegalDocument> {
    const path = locale ? `/admin/legal/${type}/${locale}` : `/admin/legal/${type}`
    const res = await client.put<LegalDocument>(path, { content })
    return res.data
}

export interface LegalFile {
    filename: string
    displayName: string
    content: string
    enabled: boolean
}

export async function getLegalFiles(type: string, locale: string): Promise<LegalFile[]> {
    const res = await client.get<LegalFile[]>(`/admin/legal/${type}/${locale}/files`)
    return res.data
}

export async function saveLegalFiles(type: string, locale: string, files: LegalFile[]): Promise<LegalFile[]> {
    const res = await client.put<LegalFile[]>(`/admin/legal/${type}/${locale}/files`, files)
    return res.data
}
