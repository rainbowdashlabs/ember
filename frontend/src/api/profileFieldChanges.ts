/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    AcknowledgeRequest,
    MemberChangeSummary,
    ProfileFieldChange,
    ProfileFieldChangeAcknowledgement,
} from './types'

export interface PagedChangesResponse {
    changes: ProfileFieldChange[]
    total: number
    offset: number
    limit: number
}

export async function getAllChanges(offset = 0, limit = 20): Promise<PagedChangesResponse> {
    const res = await client.get<PagedChangesResponse>('/profile-changes/all', { params: { offset, limit } })
    return res.data
}

export async function getPendingSummary(): Promise<MemberChangeSummary[]> {
    const res = await client.get<MemberChangeSummary[]>('/profile-changes/pending')
    return res.data
}

export async function getChanges(memberId: number): Promise<ProfileFieldChange[]> {
    const res = await client.get<ProfileFieldChange[]>(`/station-members/${memberId}/profile-changes`)
    return res.data
}

export async function acknowledge(changeId: number, data: AcknowledgeRequest): Promise<ProfileFieldChangeAcknowledgement> {
    const res = await client.post<ProfileFieldChangeAcknowledgement>(`/profile-changes/${changeId}/acknowledge`, data)
    return res.data
}

export async function acknowledgeAll(memberId: number, data: AcknowledgeRequest): Promise<ProfileFieldChangeAcknowledgement[]> {
    const res = await client.post<ProfileFieldChangeAcknowledgement[]>(`/station-members/${memberId}/profile-changes/acknowledge-all`, data)
    return res.data
}
