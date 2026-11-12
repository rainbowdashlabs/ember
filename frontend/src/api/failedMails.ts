/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {AcknowledgedMails, FailedMailPage, StationFailedMails} from './generated/schema'

/**
 * The mails that did not arrive, for the instance's administrator.
 *
 * The instance's own come with recipient and subject, the stations' only counted per station:
 * whom a station writes to is the station's business. Taking note ends the warning and keeps the mail.
 */
const BASE = '/admin/config/mailing/failed'

/** One page of the instance's own failed mails nobody has taken note of, the latest first. */
export async function getFailedInstanceMails(page: number, size: number): Promise<FailedMailPage> {
    const res = await client.get<FailedMailPage>(BASE, {params: {page, size}})
    return res.data
}

/** The stations with failed mails nobody here has taken note of. */
export async function getFailedStationMails(): Promise<StationFailedMails[]> {
    const res = await client.get<StationFailedMails[]>(`${BASE}/stations`)
    return res.data
}

export async function acknowledgeFailedMail(id: number): Promise<void> {
    await client.post(`${BASE}/${id}/acknowledge`)
}

/**
 * Takes note of the instance's own failed mails up to the latest failure the list showed, so one that
 * came in since stays.
 */
export async function acknowledgeAllFailedMails(upTo: string): Promise<AcknowledgedMails> {
    const res = await client.post<AcknowledgedMails>(`${BASE}/acknowledge-all`, {upTo})
    return res.data
}

/**
 * Takes note of a station's failed mails up to the latest one the list showed, so one that came in
 * since stays.
 */
export async function acknowledgeStationFailedMails(group: StationFailedMails): Promise<AcknowledgedMails> {
    const res = await client.post<AcknowledgedMails>(`${BASE}/stations/${group.stationUid}/acknowledge`, {upTo: group.newest})
    return res.data
}

/** Sends one of the instance's own mails the queue gave up on again, from the first provider. */
export async function requeueFailedMail(id: number): Promise<void> {
    await client.post(`${BASE}/${id}/requeue`)
}
