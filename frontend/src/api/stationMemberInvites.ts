/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {CreateInvitesRequest, CreateInvitesResponse} from './generated/schema'

export async function createInvites(body: CreateInvitesRequest): Promise<CreateInvitesResponse> {
    const res = await client.post<CreateInvitesResponse>('/station-members/invites', body)
    return res.data
}
