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
    /** The language system mails use for accounts that carry no station of their own. */
    defaultMailLocale?: string
    /** The languages this instance holds mail templates for. Read-only; the server decides. */
    availableMailLocales?: string[]
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
    /**
     * The address a mail provider reports delivery events to. It carries the instance webhook key,
     * so it is a secret in its own right - the server sends it, never the client.
     */
    deliveryWebhookUrl?: string
}

/**
 * Replaces the instance webhook key. The old address stops working at once, so whatever was
 * pointed at it has to be pointed at the new one.
 */
export async function regenerateWebhookKey(): Promise<string> {
    const res = await client.post<{deliveryWebhookUrl: string}>('/admin/config/mailing/webhook-key')
    return res.data.deliveryWebhookUrl
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

export async function sendTestMail(): Promise<void> {
    await client.post('/admin/config/mailing/test-mail')
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
    /** Rendered by the application rather than written by hand - content is read-only. */
    generated?: boolean
}

/** One section of the documents Ember ships, offered for loading into the editor. */
export interface LegalTemplate {
    displayName: string
    content: string
}

export async function getLegalFiles(type: string, locale: string): Promise<LegalFile[]> {
    const res = await client.get<LegalFile[]>(`/admin/legal/${type}/${locale}/files`)
    return res.data
}

export async function saveLegalFiles(type: string, locale: string, files: LegalFile[]): Promise<LegalFile[]> {
    const res = await client.put<LegalFile[]>(`/admin/legal/${type}/${locale}/files`, files)
    return res.data
}

/** What an import made of a document written elsewhere. */
export interface LegalImport {
    /** The document title, if the source carried one. */
    title: string | null
    /** The sections it was cut into, ready for the editor. */
    files: LegalFile[]
    /** How many numbers in the text became references. */
    references: number
    /** Numbers that look like a reference but point at no section of this document. */
    unmatched: string[]
}

/**
 * Turns a document written elsewhere into sections: the numbering leaves the headings and the
 * cross-references are rewritten onto anchors. Nothing is stored - the result comes back for the
 * editor to review and save.
 */
export async function importLegalDocument(type: string, locale: string, file: File): Promise<LegalImport> {
    const form = new FormData()
    form.append('file', file)
    const res = await client.post<LegalImport>(`/admin/legal/${type}/${locale}/import`, form)
    return res.data
}

/** The same, for a document pasted as text rather than uploaded. */
export async function importLegalMarkdown(type: string, locale: string, markdown: string): Promise<LegalImport> {
    const res = await client.post<LegalImport>(`/admin/legal/${type}/${locale}/import`, {markdown})
    return res.data
}

export async function getLegalTemplates(type: string, locale: string): Promise<LegalTemplate[]> {
    const res = await client.get<LegalTemplate[]>(`/admin/legal/${type}/${locale}/templates`)
    return res.data
}

/** One section a placeholder appears in. */
export interface PlaceholderUsage {
    type: string
    locale: string
    section: string
}

/** A `{{ name }}` token found in the legal documents, with the value configured for it. */
export interface DocumentPlaceholder {
    name: string
    value: string
    usages: PlaceholderUsage[]
}

export async function getLegalPlaceholders(): Promise<DocumentPlaceholder[]> {
    const res = await client.get<DocumentPlaceholder[]>('/admin/legal/placeholders')
    return res.data
}

export async function saveLegalPlaceholders(values: Record<string, string>): Promise<DocumentPlaceholder[]> {
    const res = await client.put<DocumentPlaceholder[]>('/admin/legal/placeholders', {values})
    return res.data
}
