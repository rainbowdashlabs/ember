/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {pageParams} from './crud'
import type {
    AcknowledgeRequest,
    EnrichedMemberChangeSummary,
    EnrichedProfileFieldChange,
    MemberIdentity,
    PagedChangesResponse,
    ProfileFieldChange,
    ProfileFieldChangeAcknowledgement,
} from './generated/schema'

/** A change to a profile answer together with whose profile it is, as the screens list one. */
export type ChangeEntry = ProfileFieldChange & {memberIdentity: MemberIdentity}

/** One page of changes, each already paired with whose profile it is. */
export type ChangePage = Omit<PagedChangesResponse, 'changes'> & {changes: ChangeEntry[]}

function flatten(enriched: EnrichedProfileFieldChange): ChangeEntry {
    return {...enriched.change, memberIdentity: enriched.memberIdentity}
}

export async function getAllChanges(offset = 0, limit = 20): Promise<ChangePage> {
    const res = await client.get<PagedChangesResponse>('/profile-changes/all', {
        params: pageParams({offset, limit}),
    })
    return {...res.data, changes: res.data.changes.map(flatten)}
}

export async function getPendingSummary(): Promise<EnrichedMemberChangeSummary[]> {
    const res = await client.get<EnrichedMemberChangeSummary[]>('/profile-changes/pending')
    return res.data
}

export async function getChanges(memberId: number): Promise<ChangeEntry[]> {
    const res = await client.get<EnrichedProfileFieldChange[]>(`/station-members/${memberId}/profile-changes`)
    return res.data.map(flatten)
}

export async function acknowledge(changeId: number, data: AcknowledgeRequest): Promise<ProfileFieldChangeAcknowledgement> {
    const res = await client.post<ProfileFieldChangeAcknowledgement>(`/profile-changes/${changeId}/acknowledge`, data)
    return res.data
}

export async function acknowledgeAll(memberId: number, data: AcknowledgeRequest): Promise<ProfileFieldChangeAcknowledgement[]> {
    const res = await client.post<ProfileFieldChangeAcknowledgement[]>(`/station-members/${memberId}/profile-changes/acknowledge-all`, data)
    return res.data
}
