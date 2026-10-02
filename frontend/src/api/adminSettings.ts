/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    ApplicationSettings,
    BackupCodesConfig,
    BulkRetireResponse,
    components,
    DocumentPlaceholder,
    HibpConfigRequest,
    HibpConfigResponse,
    LegalDocumentResponse,
    LegalFileEntry,
    LegalImportResponse,
    MailingConfigRequest,
    MailingConfigResponse,
    PasskeysConfigResponse,
    PasswordlessReport,
    PublicTheme,
    ResidueEntry,
    StationRegistrationStatus,
    TemplateSection,
    TokensConfigRequest,
    TokensConfigResponse,
    TotpConfig,
    TwoFactorCoreConfigRequest,
    TwoFactorCoreConfigResponse,
    WebAuthnConfig,
    WebhookUrlResponse,
} from './generated/schema'

export type PasskeyModeName = components['schemas']['Mode']

/** How far the instance has moved from passwords to passkeys. */
export const PasskeyMode = {
    OFF: 'OFF',
    OPTIONAL: 'OPTIONAL',
    ENCOURAGED: 'ENCOURAGED',
    PREFERRED: 'PREFERRED',
    PASSWORDLESS: 'PASSWORDLESS',
} as const satisfies Record<PasskeyModeName, PasskeyModeName>

/**
 * Replaces the instance webhook key. The old address stops working at once, so whatever was
 * pointed at it has to be pointed at the new one.
 */
export async function regenerateWebhookKey(): Promise<string> {
    const res = await client.post<WebhookUrlResponse>('/admin/config/mailing/webhook-key')
    return res.data.deliveryWebhookUrl
}

export async function getSettings(): Promise<ApplicationSettings> {
    const res = await client.get<ApplicationSettings>('/admin/settings')
    return res.data
}

/** The languages the instance holds mail templates for are the server's to say, so they are not sent. */
export async function updateSettings(
    settings: Omit<ApplicationSettings, 'availableMailLocales'>,
): Promise<ApplicationSettings> {
    const res = await client.put<ApplicationSettings>('/admin/settings', settings)
    return res.data
}

export async function isRegistrationEnabled(): Promise<boolean> {
    const res = await client.get<StationRegistrationStatus>('/public/settings/station-registration')
    return res.data.enabled
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

export async function getHibpConfig(): Promise<HibpConfigResponse> {
    const res = await client.get<HibpConfigResponse>('/admin/config/auth/hibp')
    return res.data
}

export async function updateHibpConfig(data: HibpConfigRequest): Promise<HibpConfigResponse> {
    const res = await client.put<HibpConfigResponse>('/admin/config/auth/hibp', data)
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
    const res = await client.get<WebAuthnConfig>('/admin/config/auth/webauthn')
    return res.data
}

export async function updateWebAuthnConfig(data: WebAuthnConfig): Promise<WebAuthnConfig> {
    const res = await client.put<WebAuthnConfig>('/admin/config/auth/webauthn', data)
    return res.data
}

/** The mode with the readiness the instance can check about itself, and the adoption figures. */
export async function getPasskeysConfig(): Promise<PasskeysConfigResponse> {
    const res = await client.get<PasskeysConfigResponse>('/admin/config/auth/passkeys')
    return res.data
}

/** Sends the mode by its name, which the server checks against the ones it knows. */
export async function updatePasskeysConfig(mode: string): Promise<PasskeysConfigResponse> {
    const res = await client.put<PasskeysConfigResponse>('/admin/config/auth/passkeys', {mode})
    return res.data
}

/** What would happen if the instance switched to the passwordless mode, counted. */
export async function getPasswordlessReport(): Promise<PasswordlessReport> {
    const res = await client.get<PasswordlessReport>('/admin/config/auth/passkeys/report')
    return res.data
}

/** The password holders with no exercised passkey: the group that cannot move yet. */
export async function getPasskeyResidue(): Promise<ResidueEntry[]> {
    const res = await client.get<ResidueEntry[]>('/admin/config/auth/passkeys/residue')
    return res.data
}

export async function retirePassword(accountId: number): Promise<void> {
    await client.post(`/admin/accounts/${accountId}/password/retire`)
}

export async function retireAllPasswords(): Promise<BulkRetireResponse> {
    const res = await client.post<BulkRetireResponse>('/admin/config/auth/passkeys/retire-all')
    return res.data
}

/**
 * What is left of the mailing settings once the providers became a list of their own: what belongs
 * to the instance rather than to any one provider.
 */
export async function getMailingConfig(): Promise<MailingConfigResponse> {
    const res = await client.get<MailingConfigResponse>('/admin/config/mailing')
    return res.data
}

export async function updateMailingConfig(data: MailingConfigRequest): Promise<MailingConfigResponse> {
    const res = await client.put<MailingConfigResponse>('/admin/config/mailing', {
        notificationDigestIntervalMinutes: data.notificationDigestIntervalMinutes,
    })
    return res.data
}

export async function clearMailingConfig(): Promise<void> {
    await client.delete('/admin/config/mailing')
}

export async function sendTestMail(): Promise<void> {
    await client.post('/admin/config/mailing/test-mail')
}

export async function getLegalDocument(type: string, locale?: string): Promise<LegalDocumentResponse> {
    const path = locale ? `/admin/legal/${type}/${locale}` : `/admin/legal/${type}`
    const res = await client.get<LegalDocumentResponse>(path)
    return res.data
}

export async function getLegalLocales(type: string): Promise<string[]> {
    const res = await client.get<string[]>(`/admin/legal/${type}/locales`)
    return res.data
}

export async function updateLegalDocument(
    type: string,
    content: string,
    locale?: string,
): Promise<LegalDocumentResponse> {
    const path = locale ? `/admin/legal/${type}/${locale}` : `/admin/legal/${type}`
    const res = await client.put<LegalDocumentResponse>(path, { content })
    return res.data
}

export async function getLegalFiles(type: string, locale: string): Promise<LegalFileEntry[]> {
    const res = await client.get<LegalFileEntry[]>(`/admin/legal/${type}/${locale}/files`)
    return res.data
}

export async function saveLegalFiles(
    type: string,
    locale: string,
    files: LegalFileEntry[],
): Promise<LegalFileEntry[]> {
    const res = await client.put<LegalFileEntry[]>(`/admin/legal/${type}/${locale}/files`, files)
    return res.data
}

/**
 * Turns a document written elsewhere into sections: the numbering leaves the headings and the
 * cross-references are rewritten onto anchors. Nothing is stored - the result comes back for the
 * editor to review and save.
 */
export async function importLegalDocument(type: string, locale: string, file: File): Promise<LegalImportResponse> {
    const form = new FormData()
    form.append('file', file)
    const res = await client.post<LegalImportResponse>(`/admin/legal/${type}/${locale}/import`, form)
    return res.data
}

/** The same, for a document pasted as text rather than uploaded. */
export async function importLegalMarkdown(
    type: string,
    locale: string,
    markdown: string,
): Promise<LegalImportResponse> {
    const res = await client.post<LegalImportResponse>(`/admin/legal/${type}/${locale}/import`, {markdown})
    return res.data
}

/** The sections of the documents Ember ships, offered for loading into the editor. */
export async function getLegalTemplates(type: string, locale: string): Promise<TemplateSection[]> {
    const res = await client.get<TemplateSection[]>(`/admin/legal/${type}/${locale}/templates`)
    return res.data
}

/** The `{{ name }}` tokens found in the legal documents, with the value configured for each. */
export async function getLegalPlaceholders(): Promise<DocumentPlaceholder[]> {
    const res = await client.get<DocumentPlaceholder[]>('/admin/legal/placeholders')
    return res.data
}

export async function saveLegalPlaceholders(values: Record<string, string>): Promise<DocumentPlaceholder[]> {
    const res = await client.put<DocumentPlaceholder[]>('/admin/legal/placeholders', {values})
    return res.data
}
