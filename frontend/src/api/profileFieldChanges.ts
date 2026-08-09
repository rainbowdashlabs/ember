/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {pageParams} from './crud'
import type {MemberIdentity, PageMeta} from './types'

export interface ProfileFieldChangeAcknowledgement {
    id: number
    changeId: number
    acknowledgedBy: number
    acknowledgedAt?: string
    comment?: string
    acknowledgedByName?: string
}

export interface ProfileFieldChange {
    id: number
    fieldId: number
    memberId: number
    oldValue?: string
    newValue?: string
    changedBy: number
    changedAt?: string
    requiresAcknowledgement: boolean
    changedByName?: string
    fieldName?: string
    acknowledgements: ProfileFieldChangeAcknowledgement[]
    memberName?: string | null
    memberIdentity?: MemberIdentity | null
}

export interface MemberChangeSummary {
    memberId: number
    memberName?: string
    pendingCount: number
    latestChange?: string
    identity?: MemberIdentity | null
}

export interface AcknowledgeRequest {
    comment?: string
}

interface EnrichedProfileFieldChange {
    change: ProfileFieldChange
    memberIdentity?: MemberIdentity | null
}

interface RawPagedChangesResponse extends PageMeta {
    changes: EnrichedProfileFieldChange[]
}

export interface PagedChangesResponse extends PageMeta {
    changes: ProfileFieldChange[]
}

function flatten(enriched: EnrichedProfileFieldChange): ProfileFieldChange {
    return { ...enriched.change, memberIdentity: enriched.memberIdentity ?? null }
}

export async function getAllChanges(offset = 0, limit = 20): Promise<PagedChangesResponse> {
    const res = await client.get<RawPagedChangesResponse>('/profile-changes/all', {
        params: pageParams({offset, limit}),
    })
    return {
        ...res.data,
        changes: res.data.changes.map(flatten),
    }
}

export async function getPendingSummary(): Promise<MemberChangeSummary[]> {
    const res = await client.get<MemberChangeSummary[]>('/profile-changes/pending')
    return res.data
}

export async function getChanges(memberId: number): Promise<ProfileFieldChange[]> {
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
