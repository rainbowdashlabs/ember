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

export interface TokensConfigResponse {
    tokenBytes: number
    verifyTokenHours: number
    passwordTokenHours: number
    sessionMinutes: number
    tokenPepperConfigured: boolean
}

export interface TokensConfigRequest {
    tokenBytes: number
    verifyTokenHours: number
    passwordTokenHours: number
    sessionMinutes: number
}

export interface HibpConfig {
    enabled: boolean
    endpoint: string
    staleAfterDays: number
    timeoutSeconds: number
}

export interface TwoFactorCoreConfigResponse {
    enabled: boolean
    stepUpFreshnessSeconds: number
    trustedDeviceMaxDays: number
    enrollmentGraceDays: number
    secretKeyConfigured: boolean
}

export interface TwoFactorCoreConfigRequest {
    enabled: boolean
    stepUpFreshnessSeconds: number
    trustedDeviceMaxDays: number
    enrollmentGraceDays: number
}

export interface TotpConfig {
    digits: number
    periodSeconds: number
    algorithm: string
    driftWindow: number
    issuer: string
}

export interface BackupCodesConfig {
    count: number
}

export interface WebAuthnConfig {
    rpId: string
    rpName: string
    attestation: string
    timeoutSeconds: number
    requireResidentKey: boolean
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

export async function getTokensConfig(): Promise<TokensConfigResponse> {
    const res = await client.get<TokensConfigResponse>('/admin/config/auth/tokens')
    return res.data
}

export async function updateTokensConfig(data: TokensConfigRequest): Promise<TokensConfigResponse> {
    const res = await client.put<TokensConfigResponse>('/admin/config/auth/tokens', data)
    return res.data
}

export async function generateTokenPepper(): Promise<TokensConfigResponse> {
    const res = await client.post<TokensConfigResponse>('/admin/config/auth/tokens/generate-pepper')
    return res.data
}

export async function getHibpConfig(): Promise<HibpConfig> {
    const res = await client.get<HibpConfig>('/admin/config/auth/hibp')
    return res.data
}

export async function updateHibpConfig(data: HibpConfig): Promise<HibpConfig> {
    const res = await client.put<HibpConfig>('/admin/config/auth/hibp', data)
    return res.data
}

export async function getTwoFactorCoreConfig(): Promise<TwoFactorCoreConfigResponse> {
    const res = await client.get<TwoFactorCoreConfigResponse>('/admin/config/auth/two-factor')
    return res.data
}

export async function updateTwoFactorCoreConfig(
    data: TwoFactorCoreConfigRequest,
): Promise<TwoFactorCoreConfigResponse> {
    const res = await client.put<TwoFactorCoreConfigResponse>('/admin/config/auth/two-factor', data)
    return res.data
}

export async function generateTwoFactorSecretKey(): Promise<TwoFactorCoreConfigResponse> {
    const res = await client.post<TwoFactorCoreConfigResponse>(
        '/admin/config/auth/two-factor/generate-secret-key',
    )
    return res.data
}

export async function getTotpConfig(): Promise<TotpConfig> {
    const res = await client.get<TotpConfig>('/admin/config/auth/two-factor/totp')
    return res.data
}

export async function updateTotpConfig(data: TotpConfig): Promise<TotpConfig> {
    const res = await client.put<TotpConfig>('/admin/config/auth/two-factor/totp', data)
    return res.data
}

export async function getBackupCodesConfig(): Promise<BackupCodesConfig> {
    const res = await client.get<BackupCodesConfig>('/admin/config/auth/two-factor/backup-codes')
    return res.data
}

export async function updateBackupCodesConfig(data: BackupCodesConfig): Promise<BackupCodesConfig> {
    const res = await client.put<BackupCodesConfig>('/admin/config/auth/two-factor/backup-codes', data)
    return res.data
}

export async function getWebAuthnConfig(): Promise<WebAuthnConfig> {
    const res = await client.get<WebAuthnConfig>('/admin/config/auth/two-factor/webauthn')
    return res.data
}

export async function updateWebAuthnConfig(data: WebAuthnConfig): Promise<WebAuthnConfig> {
    const res = await client.put<WebAuthnConfig>('/admin/config/auth/two-factor/webauthn', data)
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

export async function clearMailingConfig(): Promise<void> {
    await client.delete('/admin/config/mailing')
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
