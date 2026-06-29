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
}

export interface StationMemberInviteResponse {
    id: number
    stationId: number
    email: string
    firstName: string
    lastName: string
    userType: string
    groupId: number | null
    guardianOfInviteId: number | null
    invitedByMemberId: number
    expiresAt: string
    acceptedAt: string | null
    createdAt: string
}

export interface PublicStationMemberInviteResponse {
    firstName: string
    lastName: string
    email: string
    userType: string
    stationName: string
    accepted: boolean
    expired: boolean
}

export interface AcceptResult {
    accountId: number
    memberId: number
    stationId: number
}

export async function createInvites(body: CreateInvitesRequest): Promise<StationMemberInviteResponse[]> {
    const res = await client.post<StationMemberInviteResponse[]>('/station-members/invites', body)
    return res.data
}

export async function listInvites(): Promise<StationMemberInviteResponse[]> {
    const res = await client.get<StationMemberInviteResponse[]>('/station-members/invites')
    return res.data
}

export async function deleteInvite(id: number): Promise<void> {
    await client.delete(`/station-members/invites/${id}`)
}

export async function publicLookup(token: string): Promise<PublicStationMemberInviteResponse> {
    const res = await client.get<PublicStationMemberInviteResponse>(`/public/station-invite/${token}`)
    return res.data
}

export async function publicAccept(token: string, password: string): Promise<AcceptResult> {
    const res = await client.post<AcceptResult>(`/public/station-invite/${token}/accept`, {password})
    return res.data
}
