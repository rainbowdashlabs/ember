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
