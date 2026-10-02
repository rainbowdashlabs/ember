/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {
    SmtpEncryption,
    type MailDashboard,
    type MailFallbackChain,
    type MailFallbackPayload,
    type MailTestResponse,
    type NotificationSchedulePayload,
    type RequeuedMails,
    type WebhookUrl,
} from './generated/schema'

/**
 * One provider in the order mail is tried through, as a row of the list being edited.
 *
 * The first is simply the first, not a provider of a different kind: the list is worked from the
 * top, and an entry hands over once its attempts or its daily allowance are spent.
 *
 * Secrets arrive masked as `********`. Sending the mask back means "leave it as it was", so the
 * list can be reordered without retyping every password in it. The address a provider reports
 * delivery events to is handed out by the server, so a row added on this side has none yet.
 */
export type MailProvider = Omit<MailFallbackPayload, 'deliveryWebhookUrl'>
    & Partial<Pick<MailFallbackPayload, 'deliveryWebhookUrl'>>

/** The instance list, still carrying the attempts field its first provider used to own. */
export type MailProviderChain = Omit<MailFallbackChain, 'fallbacks'> & {fallbacks: MailProvider[]}

/** What an empty row starts as, so every caller adds the same shape. */
export function emptyMailProvider(): MailProvider {
    return {
        provider: 'SMTP',
        smtpHost: '',
        smtpPort: 587,
        smtpEncryption: SmtpEncryption.STARTTLS,
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

export async function getInstanceProviders(): Promise<MailFallbackChain> {
    const res = await client.get<MailFallbackChain>('/admin/config/mailing/providers')
    return res.data
}

export async function updateInstanceProviders(chain: MailProviderChain): Promise<MailFallbackChain> {
    const res = await client.put<MailFallbackChain>('/admin/config/mailing/providers', chain)
    return res.data
}

/**
 * Empties the station's list. The deliberate way to stop sending: a save can no longer do it by
 * accident, so this is the only route that leaves nothing behind.
 */
export async function clearStationProviders(): Promise<void> {
    await client.delete('/station/manage/mail')
}

export async function getStationProviders(): Promise<MailFallbackPayload[]> {
    const res = await client.get<MailFallbackPayload[]>('/station/manage/mail/providers')
    return res.data
}

export async function updateStationProviders(providers: MailProvider[]): Promise<MailFallbackPayload[]> {
    const res = await client.put<MailFallbackPayload[]>('/station/manage/mail/providers', providers)
    return res.data
}

/**
 * Tries one provider of the station's list. Every entry can be reached, not only the first: a
 * provider further down carries the post once those above it are spent, so being unable to try it
 * means finding out it was misconfigured only when it is needed.
 */
export async function testStationProvider(position: number, recipient?: string): Promise<MailTestResponse> {
    const res = await client.post<MailTestResponse>(
        `/station/manage/mail/providers/${position}/test`,
        recipient ? {recipient} : {},
    )
    return res.data
}

/**
 * The same for the instance list. An address is required here: the instance provider is tried by
 * sending through it, which is the only thing that says whether it delivers.
 */
export async function testInstanceProvider(position: number, recipient: string): Promise<MailTestResponse> {
    const res = await client.post<MailTestResponse>(
        `/admin/config/mailing/providers/${position}/test`,
        {recipient},
    )
    return res.data
}

/**
 * Puts mails a dead worker left in sending back into the queue. Without an id every left-behind
 * mail of that owner goes back.
 */
export async function requeueInstanceStuckMails(id?: number): Promise<RequeuedMails> {
    const res = await client.post<RequeuedMails>('/admin/config/mailing/stuck/requeue', null,
        {params: id === undefined ? {} : {id}})
    return res.data
}

export async function requeueStationStuckMails(id?: number): Promise<RequeuedMails> {
    const res = await client.post<RequeuedMails>('/station/manage/mail/stuck/requeue', null,
        {params: id === undefined ? {} : {id}})
    return res.data
}

/**
 * Lifts a block by hand, for when the relay is known to be off the list again and nobody wants to
 * wait out the week the block would otherwise stand.
 */
export async function liftInstanceBlock(provider: string, domain: string): Promise<void> {
    await client.delete('/admin/config/mailing/blocks', {params: {provider, domain}})
}

export async function liftStationBlock(provider: string, domain: string): Promise<void> {
    await client.delete('/station/manage/mail/blocks', {params: {provider, domain}})
}

/** What has become of the instance's post. */
export async function getInstanceMailDashboard(): Promise<MailDashboard> {
    const res = await client.get<MailDashboard>('/admin/config/mailing/dashboard')
    return res.data
}

export async function getStationMailDashboard(): Promise<MailDashboard> {
    const res = await client.get<MailDashboard>('/station/manage/mail/dashboard')
    return res.data
}

/** The address a provider reports delivery events to, and whether its signature is checked. */
export async function getStationWebhook(): Promise<WebhookUrl> {
    const res = await client.get<WebhookUrl>('/station/manage/mail/webhook')
    return res.data
}

/**
 * Stores the signing secret the provider issued. An empty value stops signatures being checked.
 */
export async function saveStationSigningSecret(secret: string): Promise<WebhookUrl> {
    const res = await client.put<WebhookUrl>('/station/manage/mail/signing-secret', {secret})
    return res.data
}

/**
 * When this station's gathered notifications go out.
 *
 * <p>An empty list means the station has asked for nothing and the number the operator set for the
 * whole installation decides, which is what every station did before it could choose. That number
 * comes along as `floorMinutes`, because it is also the shortest gap allowed between two mails and
 * a station asking for more often than that will not get it.
 */
export async function getNotificationSchedule(): Promise<NotificationSchedulePayload> {
    const res = await client.get<NotificationSchedulePayload>('/station/manage/notifications')
    return res.data
}

/** Writes the times, or gives them back by sending none. */
export async function saveNotificationSchedule(sendTimes: string[]): Promise<void> {
    await client.put('/station/manage/notifications', {sendTimes, floorMinutes: 0})
}

/** Replaces this station's webhook key, retiring its old address at once. */
export async function regenerateStationWebhookKey(): Promise<string> {
    const res = await client.post<WebhookUrl>('/station/manage/mail/webhook')
    return res.data.deliveryWebhookUrl
}
