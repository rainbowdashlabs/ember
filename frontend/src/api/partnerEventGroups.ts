/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {EventGroupDetail, EventGroupSignUp} from './generated/schema'

function base(stationUid: string, eventGroupId: number): string {
    return `/federated/${stationUid}/events/groups/${eventGroupId}`
}

/** An event group a partner station shares, with the standing of the reader and everybody they look after. */
export async function getEventGroup(stationUid: string, eventGroupId: number): Promise<EventGroupDetail> {
    return (await client.get<EventGroupDetail>(base(stationUid, eventGroupId))).data
}

/** Signs the reader, or somebody they look after, up for a partner's event group. */
export async function signUp(stationUid: string, eventGroupId: number, parts: number[], memberId?: number): Promise<EventGroupSignUp> {
    return (await client.post<EventGroupSignUp>(`${base(stationUid, eventGroupId)}/sign-up`, {parts, memberId})).data
}

/** Changes the parts chosen with a sign-up for a partner's event group. */
export async function changeParts(stationUid: string, eventGroupId: number, parts: number[], memberId?: number): Promise<EventGroupSignUp> {
    return (await client.put<EventGroupSignUp>(`${base(stationUid, eventGroupId)}/sign-up`, {parts, memberId})).data
}

/** Withdraws a sign-up for a partner's event group. */
export async function withdraw(stationUid: string, eventGroupId: number, memberId?: number): Promise<void> {
    await client.delete(`${base(stationUid, eventGroupId)}/sign-up`, {params: {memberId}})
}
