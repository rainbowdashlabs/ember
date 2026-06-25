/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {InviteRequest, InviteResponse, MessageResponse, ResetPasswordRequest} from './types'

export async function invite(data: InviteRequest): Promise<InviteResponse> {
    const res = await client.post<InviteResponse>('/members/invite', data)
    return res.data
}

export async function updateAccount(accountId: number, data: {
    email?: string;
    firstName?: string;
    lastName?: string
}): Promise<MessageResponse> {
    const res = await client.put<MessageResponse>(`/members/${accountId}`, data)
    return res.data
}

export async function resetPassword(data: ResetPasswordRequest): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/members/reset-password', data)
    return res.data
}

// -- Page-editor picker. PAGE_EDIT-gated. --

export interface MemberSearchResult {
    memberUid: string
    displayName: string
    userType: string | null
    displayTag: string | null
    displayTagColor: string | null
    avatarUrl: string | null
}

export async function searchMembers(query?: string, limit = 20): Promise<MemberSearchResult[]> {
    const params: Record<string, string | number> = {limit}
    if (query) params.q = query
    const res = await client.get<MemberSearchResult[]>('/members/search', {params})
    return res.data
}

/** Resolves a single member by its UUID for picker display. Returns {@code null} if not found. */
export async function getMemberPickerByUid(uid: string): Promise<MemberSearchResult | null> {
    const res = await client.get<MemberSearchResult[]>('/members/search', {params: {uid}})
    return res.data[0] ?? null
}
