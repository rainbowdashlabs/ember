/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    GuardianInput,
    PublicWaitlistFormResponse,
    PublicWaitlistSummary,
    WaitingList,
    WaitingListEntry,
    WaitingListEntryWithScore,
    WaitingListField,
    WaitingListInvite,
    WaitingListInviteInfo,
    WaitingListPublicStatus,
    WaitingListWithCount,
} from './types'

// --- Management ---

export async function listAll(): Promise<WaitingListWithCount[]> {
    const res = await client.get<WaitingListWithCount[]>('/waiting-lists')
    return res.data
}

export async function create(data: {
    name: string
    description?: string
    scoringFormula?: string | null
    confirmIntervalDays?: number
    testingGroupId?: number | null
    joinGroupId?: number | null
    attendanceThreshold?: number
    isPublic?: boolean
}): Promise<WaitingList> {
    const res = await client.post<WaitingList>('/waiting-lists', data)
    return res.data
}

export async function getById(id: number): Promise<WaitingList> {
    const res = await client.get<WaitingList>(`/waiting-lists/${id}`)
    return res.data
}

export async function update(id: number, data: {
    name: string
    description?: string
    scoringFormula?: string | null
    confirmIntervalDays?: number
    testingGroupId?: number | null
    joinGroupId?: number | null
    attendanceThreshold?: number
    isPublic?: boolean
}): Promise<WaitingList> {
    const res = await client.put<WaitingList>(`/waiting-lists/${id}`, data)
    return res.data
}

export async function deleteList(id: number): Promise<void> {
    await client.delete(`/waiting-lists/${id}`)
}

export async function updateVisibleFields(id: number, fieldIds: number[]): Promise<WaitingList> {
    const res = await client.put<WaitingList>(`/waiting-lists/${id}/visible-fields`, { fieldIds })
    return res.data
}

// Fields

export async function listFields(listId: number): Promise<WaitingListField[]> {
    const res = await client.get<WaitingListField[]>(`/waiting-lists/${listId}/fields`)
    return res.data
}

export async function createField(listId: number, data: { name: string; fieldType: string; config?: string; position: number; required: boolean; isPublic?: boolean }): Promise<WaitingListField> {
    const res = await client.post<WaitingListField>(`/waiting-lists/${listId}/fields`, data)
    return res.data
}

export async function updateField(listId: number, fieldId: number, data: { name: string; fieldType: string; config?: string; position: number; required: boolean; isPublic?: boolean }): Promise<WaitingListField> {
    const res = await client.put<WaitingListField>(`/waiting-lists/${listId}/fields/${fieldId}`, data)
    return res.data
}

export async function deleteField(listId: number, fieldId: number): Promise<void> {
    await client.delete(`/waiting-lists/${listId}/fields/${fieldId}`)
}

// Invites

export async function listInvites(listId: number): Promise<WaitingListInvite[]> {
    const res = await client.get<WaitingListInvite[]>(`/waiting-lists/${listId}/invites`)
    return res.data
}

export async function createInvite(listId: number, data?: { maxUses?: number; expiresAt?: string | null }): Promise<WaitingListInvite> {
    const res = await client.post<WaitingListInvite>(`/waiting-lists/${listId}/invites`, data ?? {})
    return res.data
}

export async function deleteInvite(listId: number, inviteId: number): Promise<void> {
    await client.delete(`/waiting-lists/${listId}/invites/${inviteId}`)
}

// Entries

export async function listEntries(listId: number): Promise<WaitingListEntryWithScore[]> {
    const res = await client.get<WaitingListEntryWithScore[]>(`/waiting-lists/${listId}/entries`)
    return res.data
}

export async function createEntry(listId: number, data: { firstname: string; lastname?: string; guardians?: GuardianInput[]; values?: Record<number, unknown>; notes?: string }): Promise<WaitingListEntry> {
    const res = await client.post<WaitingListEntry>(`/waiting-lists/${listId}/entries`, data)
    return res.data
}

export async function updateEntry(listId: number, entryId: number, data: { firstname: string; lastname?: string; guardians?: GuardianInput[]; values?: Record<number, unknown>; notes?: string }): Promise<WaitingListEntry> {
    const res = await client.put<WaitingListEntry>(`/waiting-lists/${listId}/entries/${entryId}`, data)
    return res.data
}

export async function updateCreatedAt(listId: number, entryId: number, createdAt: string): Promise<WaitingListEntry> {
    const res = await client.put<WaitingListEntry>(`/waiting-lists/${listId}/entries/${entryId}/created-at`, { createdAt })
    return res.data
}

export async function deleteEntry(listId: number, entryId: number): Promise<void> {
    await client.delete(`/waiting-lists/${listId}/entries/${entryId}`)
}

// State transitions

export async function inviteEntry(listId: number, entryId: number): Promise<WaitingListEntry> {
    const res = await client.post<WaitingListEntry>(`/waiting-lists/${listId}/entries/${entryId}/invite`)
    return res.data
}

export async function moveToTesting(listId: number, entryId: number): Promise<WaitingListEntry> {
    const res = await client.post<WaitingListEntry>(`/waiting-lists/${listId}/entries/${entryId}/testing`)
    return res.data
}

export async function moveToJoined(listId: number, entryId: number): Promise<WaitingListEntry> {
    const res = await client.post<WaitingListEntry>(`/waiting-lists/${listId}/entries/${entryId}/join`)
    return res.data
}

export async function withdrawEntry(listId: number, entryId: number): Promise<void> {
    await client.post(`/waiting-lists/${listId}/entries/${entryId}/withdraw`)
}

// --- Public ---

export async function getInviteInfo(code: string): Promise<WaitingListInviteInfo> {
    const res = await client.get<WaitingListInviteInfo>(`/public/waiting-list/invite/${code}`)
    return res.data
}

export async function register(data: { inviteCode: string; firstname: string; lastname?: string; guardians?: GuardianInput[]; values?: Record<number, unknown>; notes?: string }): Promise<{ accessToken: string }> {
    const res = await client.post<{ accessToken: string }>('/public/waiting-list/register', data)
    return res.data
}

export async function getEntryStatus(token: string): Promise<WaitingListPublicStatus> {
    const res = await client.get<WaitingListPublicStatus>(`/public/waiting-list/entry/${token}`)
    return res.data
}

export async function removeEntry(token: string): Promise<void> {
    await client.post(`/public/waiting-list/entry/${token}/remove`)
}

export async function confirmInterest(token: string): Promise<void> {
    await client.post(`/public/waiting-list/entry/${token}/confirm`)
}

// --- Approve / Reject ---

export async function approveEntry(listId: number, entryId: number): Promise<WaitingListEntry> {
    const res = await client.post<WaitingListEntry>(`/waiting-lists/${listId}/entries/${entryId}/approve`)
    return res.data
}

export async function rejectEntry(listId: number, entryId: number): Promise<void> {
    await client.post(`/waiting-lists/${listId}/entries/${entryId}/reject`)
}

// --- Public Waitlist Registration ---

export async function listPublicWaitlists(stationUid: string): Promise<PublicWaitlistSummary[]> {
    const res = await client.get<PublicWaitlistSummary[]>(`/public/station/${stationUid}/waitlists`)
    return res.data
}

export async function getPublicWaitlistForm(stationUid: string, listId: number): Promise<PublicWaitlistFormResponse> {
    const res = await client.get<PublicWaitlistFormResponse>(`/public/station/${stationUid}/waitlists/${listId}/form`)
    return res.data
}

export async function submitPublicRegistration(stationUid: string, listId: number, data: {
    firstname: string
    lastname?: string
    email: string
    guardians?: GuardianInput[]
    values?: Record<number, unknown>
    notes?: string
}): Promise<{ status: string }> {
    const res = await client.post<{ status: string }>(`/public/station/${stationUid}/waitlists/${listId}/register`, data)
    return res.data
}

export async function verifyPublicRegistration(token: string): Promise<{ status: string }> {
    const res = await client.get<{ status: string }>(`/public/waitlist/verify/${token}`)
    return res.data
}
