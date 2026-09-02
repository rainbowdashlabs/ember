/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface GuardianRequest {
    email: string
    firstName: string
    lastName: string
}

export interface InviteEntry {
    email: string
    firstName: string
    lastName: string
    userType: string
    groupId: number | null
    guardians?: GuardianRequest[]
}

export interface CreateInvitesRequest {
    invites: InviteEntry[]
    /** Whether the setup mails leave with the accounts. Absent means they do. */
    sendSetupMail?: boolean
}

export interface ProvisionedMemberResponse {
    memberId: number
    accountId: number
    email: string
    firstName: string
    lastName: string
    userType: string
    accountCreated: boolean
    membershipCreated: boolean
}

export interface FailedInviteResponse {
    email: string
    reason: string
}

export interface CreateInvitesResponse {
    provisioned: ProvisionedMemberResponse[]
    failed: FailedInviteResponse[]
}

export async function createInvites(body: CreateInvitesRequest): Promise<CreateInvitesResponse> {
    const res = await client.post<CreateInvitesResponse>('/station-members/invites', body)
    return res.data
}
