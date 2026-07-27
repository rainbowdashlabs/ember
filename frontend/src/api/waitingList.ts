/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
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

interface WaitingListRequest {
    name: string
    description?: string
    scoringFormula?: string | null
    confirmIntervalDays?: number
    testingGroupId?: number | null
    joinGroupId?: number | null
    attendanceThreshold?: number
    isPublic?: boolean
}

interface FieldRequest {
    name: string
    fieldType: string
    config?: string
    position: number
    required: boolean
    isPublic?: boolean
}

interface EntryRequest {
    firstname: string
    lastname?: string
    guardians?: GuardianInput[]
    values?: Record<number, unknown>
    notes?: string
}

const lists = createCrudResource<
    WaitingListWithCount,
    WaitingListRequest,
    WaitingListRequest,
    WaitingList,
    WaitingList
>('/waiting-lists')

const fields = createScopedCrudResource<
    WaitingListField,
    FieldRequest
>((listId: number) => `/waiting-lists/${listId}/fields`)

const invites = createScopedCrudResource<
    WaitingListInvite
>((listId: number) => `/waiting-lists/${listId}/invites`)

const entries = createScopedCrudResource<
    WaitingListEntryWithScore,
    EntryRequest,
    EntryRequest,
    WaitingListEntryWithScore,
    WaitingListEntry
>((listId: number) => `/waiting-lists/${listId}/entries`)

// --- Management ---

export const listAll = lists.list
export const create = lists.create
export const getById = lists.get
export const update = lists.update
export const deleteList = lists.remove

export async function updateVisibleFields(id: number, fieldIds: number[]): Promise<WaitingList> {
    const res = await client.put<WaitingList>(`/waiting-lists/${id}/visible-fields`, { fieldIds })
    return res.data
}

// Fields

export const listFields = fields.list
export const createField = fields.create
export const updateField = fields.update
export const deleteField = fields.remove

// Invites

export const listInvites = invites.list
export const deleteInvite = invites.remove

export async function createInvite(listId: number, data?: { maxUses?: number; expiresAt?: string | null }): Promise<WaitingListInvite> {
    const res = await client.post<WaitingListInvite>(`/waiting-lists/${listId}/invites`, data ?? {})
    return res.data
}

// Entries

export const listEntries = entries.list
export const createEntry = entries.create
export const updateEntry = entries.update
export const deleteEntry = entries.remove

export async function updateCreatedAt(listId: number, entryId: number, createdAt: string): Promise<WaitingListEntry> {
    const res = await client.put<WaitingListEntry>(`/waiting-lists/${listId}/entries/${entryId}/created-at`, { createdAt })
    return res.data
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

export async function register(data: {
    inviteCode: string
    firstname: string
    lastname?: string
    guardians?: GuardianInput[]
    values?: Record<number, unknown>
    notes?: string
    consentVersion: string
    privacyVersion: string
    tosVersion: string
}): Promise<{ accessToken: string }> {
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
    consentVersion: string
    privacyVersion: string
    tosVersion: string
}): Promise<{ status: string }> {
    const res = await client.post<{ status: string }>(`/public/station/${stationUid}/waitlists/${listId}/register`, data)
    return res.data
}

export async function verifyPublicRegistration(token: string): Promise<{ status: string }> {
    const res = await client.get<{ status: string }>(`/public/waitlist/verify/${token}`)
    return res.data
}
