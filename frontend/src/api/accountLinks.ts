/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {AssociationLinkState, LinkPrompt, LinkState, MemberLink, MemberLinkResponse} from './generated/schema'

/** The requests of stations and associations waiting for the signed-in account, oldest first. */
export async function waitingRequests(): Promise<LinkPrompt[]> {
    const res = await client.get<LinkPrompt[]>('/account/link-requests')
    return res.data
}

/**
 * The request a mailed link opens, for the signed-in account it names. Answers nothing: the person
 * accepts or declines on the screen it opens.
 */
export async function openedRequest(token: string): Promise<LinkPrompt> {
    const res = await client.get<LinkPrompt>(`/account/link-requests/by-token/${encodeURIComponent(token)}`)
    return res.data
}

export async function acceptRequest(uid: string): Promise<void> {
    await client.post(`/account/link-requests/${uid}/accept`)
}

export async function declineRequest(uid: string): Promise<void> {
    await client.post(`/account/link-requests/${uid}/decline`)
}

/** Where the latest link request of every member of the station stands. */
export async function memberLinks(): Promise<MemberLink[]> {
    const res = await client.get<MemberLink[]>('/member-links')
    return res.data
}

/** Where the latest link request of one member stands, or null where the station never asked. */
export async function memberLink(memberId: number): Promise<LinkState | null> {
    const res = await client.get<MemberLinkResponse>(`/member-links/${memberId}`)
    return res.data.link
}

export async function sendAgain(memberId: number): Promise<LinkState> {
    const res = await client.post<LinkState>(`/member-links/${memberId}/send-again`)
    return res.data
}

/** The association's requests to existing accounts that wait, were declined or ran out, newest first. */
export async function associationRequests(): Promise<AssociationLinkState[]> {
    const res = await client.get<AssociationLinkState[]>('/cluster/link-requests')
    return res.data
}

export async function sendAssociationRequestAgain(uid: string): Promise<AssociationLinkState> {
    const res = await client.post<AssociationLinkState>(`/cluster/link-requests/${uid}/send-again`)
    return res.data
}
