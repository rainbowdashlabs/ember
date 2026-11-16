/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {CommentResponse, EventGroupDetail, EventGroupSignUp} from './generated/schema'
import type {CommentSource} from './comments'
import {listCompletions} from './stationMembers'

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

/**
 * The thread under an event group a partner station shares, kept at the partner. A mention offers
 * this station's own members, who are the ones reading along here; only authors change or remove
 * anything.
 */
export function partnerEventGroupCommentSource(stationUid: string, eventGroupId: number): CommentSource {
    const comments = `${base(stationUid, eventGroupId)}/comments`
    return {
        list: async () => (await client.get<CommentResponse[]>(comments)).data,
        create: (parentId, content) => client.post(comments, {parentId, content}),
        update: (commentId, content) => client.put(`${comments}/${commentId}`, {content}),
        remove: commentId => client.delete(`${comments}/${commentId}`),
        mentionables: async () => ({members: await listCompletions(), groups: []}),
        moderator: null,
    }
}
