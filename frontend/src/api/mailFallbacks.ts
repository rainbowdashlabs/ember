/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * One provider in the order mail is tried through, after the first.
 *
 * Secrets arrive masked as `********`. Sending the mask back means "leave it as it was", so a chain
 * can be reordered without retyping every password in it.
 */
export interface MailFallback {
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
}

/** The instance chain: how many attempts its first provider gets, and who comes after it. */
export interface MailFallbackChain {
    attempts: number
    fallbacks: MailFallback[]
}

export async function getInstanceFallbacks(): Promise<MailFallbackChain> {
    const res = await client.get<MailFallbackChain>('/admin/config/mailing/fallbacks')
    return res.data
}

export async function updateInstanceFallbacks(chain: MailFallbackChain): Promise<MailFallbackChain> {
    const res = await client.put<MailFallbackChain>('/admin/config/mailing/fallbacks', chain)
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

export async function getStationFallbacks(): Promise<MailFallback[]> {
    const res = await client.get<MailFallback[]>('/station/manage/mail/fallbacks')
    return res.data
}

export async function updateStationFallbacks(fallbacks: MailFallback[]): Promise<MailFallback[]> {
    const res = await client.put<MailFallback[]>('/station/manage/mail/fallbacks', fallbacks)
    return res.data
}
