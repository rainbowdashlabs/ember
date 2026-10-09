/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {InstanceMailStation, MailReplyTo} from './generated/schema'

/**
 * Stations sending their own mail through the instance's providers, after their own.
 *
 * The administrator grants and withdraws it, per station or for many at once, and may give a
 * station a daily limit of its own. Every change asks for a fresh second factor; the client brings
 * up the prompt by itself. A station only reads where it stands.
 */

/** Every station and whether it may send through the instance's providers. */
export async function listInstanceMailStations(): Promise<InstanceMailStation[]> {
    const res = await client.get<InstanceMailStation[]>('/admin/config/mailing/stations')
    return res.data
}

export async function getInstanceMailStation(stationUid: string): Promise<InstanceMailStation> {
    const res = await client.get<InstanceMailStation>(`/admin/config/mailing/stations/${stationUid}`)
    return res.data
}

/** Grants one station, or changes its daily limit. No limit leaves only the stations' share. */
export async function grantInstanceMail(stationUid: string, dailyLimit: number | null): Promise<InstanceMailStation> {
    const res = await client.put<InstanceMailStation>(`/admin/config/mailing/stations/${stationUid}`, {dailyLimit})
    return res.data
}

export async function withdrawInstanceMail(stationUid: string): Promise<InstanceMailStation> {
    const res = await client.delete<InstanceMailStation>(`/admin/config/mailing/stations/${stationUid}`)
    return res.data
}

/** Grants several stations at once, all with the same daily limit, and answers with the whole list. */
export async function grantInstanceMailTo(stationUids: string[], dailyLimit: number | null): Promise<InstanceMailStation[]> {
    const res = await client.post<InstanceMailStation[]>('/admin/config/mailing/stations/grant', {stationUids, dailyLimit})
    return res.data
}

export async function withdrawInstanceMailFrom(stationUids: string[]): Promise<InstanceMailStation[]> {
    const res = await client.post<InstanceMailStation[]>('/admin/config/mailing/stations/withdraw', {stationUids})
    return res.data
}

/** Where this station stands: whether the instance carries its mail, its limit and today's use. */
export async function getOwnInstanceMail(): Promise<InstanceMailStation> {
    const res = await client.get<InstanceMailStation>('/station/manage/mail/instance')
    return res.data
}

/** Where replies to this station's mail go, through whichever provider carries it. */
export async function getStationReplyTo(): Promise<MailReplyTo> {
    const res = await client.get<MailReplyTo>('/station/manage/mail/reply-to')
    return res.data
}

/** Sets the reply address. An empty one sends replies back to the sender address again. */
export async function saveStationReplyTo(replyTo: string): Promise<MailReplyTo> {
    const res = await client.put<MailReplyTo>('/station/manage/mail/reply-to', {replyTo})
    return res.data
}
