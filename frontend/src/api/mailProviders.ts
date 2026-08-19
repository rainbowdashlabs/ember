/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * One provider in the order mail is tried through.
 *
 * The first is simply the first, not a provider of a different kind: the list is worked from the
 * top, and an entry hands over once its attempts or its daily allowance are spent.
 *
 * Secrets arrive masked as `********`. Sending the mask back means "leave it as it was", so the
 * list can be reordered without retyping every password in it.
 */
export interface MailProvider {
    provider: string
    smtpHost: string
    smtpPort: number
    smtpSsl: boolean
    smtpUser: string
    smtpPassword: string
    apiKey: string
    senderAddress: string
    senderName: string
    /** How many attempts this provider gets before the next one takes over. */
    attempts: number
    /** How many mails it may send in a day, or zero for no limit. */
    dailySendLimit: number
    /** The provider name shown to members of the station. Unused for the instance list. */
    providerName: string
    /** The provider website shown to members of the station. Unused for the instance list. */
    providerUrl: string
    /**
     * The address this provider reports delivery events to. Each entry gets one of its own, because
     * the address ends in the report format the provider sends. The server hands it out; sending it
     * back is ignored.
     */
    deliveryWebhookUrl?: string
}

/** The instance list, still carrying the attempts field its first provider used to own. */
export interface MailProviderChain {
    attempts: number
    fallbacks: MailProvider[]
}

/** What an empty row starts as, so every caller adds the same shape. */
export function emptyMailProvider(): MailProvider {
    return {
        provider: 'SMTP',
        smtpHost: '',
        smtpPort: 587,
        smtpSsl: false,
        smtpUser: '',
        smtpPassword: '',
        apiKey: '',
        senderAddress: '',
        senderName: '',
        attempts: 2,
        dailySendLimit: 0,
        providerName: '',
        providerUrl: '',
    }
}

export async function getInstanceProviders(): Promise<MailProviderChain> {
    const res = await client.get<MailProviderChain>('/admin/config/mailing/providers')
    return res.data
}

export async function updateInstanceProviders(chain: MailProviderChain): Promise<MailProviderChain> {
    const res = await client.put<MailProviderChain>('/admin/config/mailing/providers', chain)
    return res.data
}

/**
 * Empties the station's list. The deliberate way to stop sending: a save can no longer do it by
 * accident, so this is the only route that leaves nothing behind.
 */
export async function clearStationProviders(): Promise<void> {
    await client.delete('/station/manage/mail')
}

export async function getStationProviders(): Promise<MailProvider[]> {
    const res = await client.get<MailProvider[]>('/station/manage/mail/providers')
    return res.data
}

export async function updateStationProviders(providers: MailProvider[]): Promise<MailProvider[]> {
    const res = await client.put<MailProvider[]>('/station/manage/mail/providers', providers)
    return res.data
}

/** The outcome of trying one provider against its relay, without sending anything. */
export interface MailProviderTestResult {
    success: boolean
    error?: string | null
}

/**
 * Tries one provider of the station's list. Every entry can be reached, not only the first: a
 * provider further down carries the post once those above it are spent, so being unable to try it
 * means finding out it was misconfigured only when it is needed.
 */
export async function testStationProvider(position: number, recipient?: string): Promise<MailProviderTestResult> {
    const res = await client.post<MailProviderTestResult>(
        `/station/manage/mail/providers/${position}/test`,
        recipient ? {recipient} : {},
    )
    return res.data
}

/**
 * The same for the instance list. An address is required here: the instance provider is tried by
 * sending through it, which is the only thing that says whether it delivers.
 */
export async function testInstanceProvider(position: number, recipient: string): Promise<MailProviderTestResult> {
    const res = await client.post<MailProviderTestResult>(
        `/admin/config/mailing/providers/${position}/test`,
        {recipient},
    )
    return res.data
}

/** How one provider of the list stands today. */
export interface ProviderStanding {
    position: number
    provider: string
    senderAddress: string
    attempts: number
    dailySendLimit: number
    sentToday: number
    /** How many mails sit at this provider right now, which is what says who carries the next one. */
    waiting: number
    /** Whether its allowance is spent, so the next one is carrying the post. */
    exhausted: boolean
}

/** One mail as the overview shows it. The body is deliberately absent. */
export interface MailRecord {
    id: number
    recipient: string
    subject: string
    createdAt: string
    sentAt: string | null
    status: string
    deliveryStatus: string
    deliveryDetail: string | null
    attempts: number
    providerPosition: number
    /**
     * Whether anything in the list could still carry this one. False on a waiting mail means it is
     * not merely queued but stuck.
     */
    reachable: boolean
}

/** A provider a receiving domain refuses outright. */
export interface ProviderBlock {
    provider: string
    recipientDomain: string
    reason: string | null
    firstBlockedAt: string
    lastBlockedAt: string
    expiresAt: string
}

/** What has become of the post. */
export interface MailDashboard {
    pending: number
    sending: number
    sent: number
    failed: number
    /** Left in sending by a worker that died. Nothing retries these. */
    stuck: number
    oldestPendingAt: string | null
    providers: ProviderStanding[]
    recent: MailRecord[]
    blocks: ProviderBlock[]
}

export async function getInstanceMailDashboard(): Promise<MailDashboard> {
    const res = await client.get<MailDashboard>('/admin/config/mailing/dashboard')
    return res.data
}

export async function getStationMailDashboard(): Promise<MailDashboard> {
    const res = await client.get<MailDashboard>('/station/manage/mail/dashboard')
    return res.data
}

/** The address a provider reports delivery events to, and whether its signature is checked. */
export interface WebhookInfo {
    deliveryWebhookUrl: string
    signingSecretSet: boolean
}

export async function getStationWebhook(): Promise<WebhookInfo> {
    const res = await client.get<WebhookInfo>('/station/manage/mail/webhook')
    return res.data
}

/**
 * Stores the signing secret the provider issued. An empty value stops signatures being checked.
 */
export async function saveStationSigningSecret(secret: string): Promise<WebhookInfo> {
    const res = await client.put<WebhookInfo>('/station/manage/mail/signing-secret', {secret})
    return res.data
}

/** Replaces this station's webhook key, retiring its old address at once. */
export async function regenerateStationWebhookKey(): Promise<string> {
    const res = await client.post<{deliveryWebhookUrl: string}>('/station/manage/mail/webhook')
    return res.data.deliveryWebhookUrl
}
