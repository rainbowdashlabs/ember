/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
import type {
    components,
    PublicWaitlistFormResponse,
    PublicWaitlistRegistrationRequest,
    PublicWaitlistSummary,
    WaitingList,
    WaitingListAccessResponse,
    WaitingListCreatedAtRequest,
    WaitingListEntry,
    WaitingListEntryRequest,
    WaitingListEntryWithScore,
    WaitingListField,
    WaitingListFieldRequest,
    WaitingListInvitationAnswerRequest,
    WaitingListInvitationRequest,
    WaitingListInvite,
    WaitingListInviteInfo,
    WaitingListInviteRequest,
    WaitingListPublicStatus,
    WaitingListRegisterRequest,
    WaitingListRegistrationStatus,
    WaitingListRequest,
    WaitingListVisibleFieldsRequest,
    WaitingListWithCount,
} from './generated/schema'

export type WaitingListEntryStatusName = components['schemas']['WaitingListEntryStatus']

export const WaitingListEntryStatus = {
    PENDING: 'PENDING',
    WAITING: 'WAITING',
    INVITED: 'INVITED',
    TESTING: 'TESTING',
    WITHDRAWN: 'WITHDRAWN',
    JOINED: 'JOINED',
} as const satisfies Record<WaitingListEntryStatusName, WaitingListEntryStatusName>

export type WaitingListAnswerName = components['schemas']['WaitingListAnswer']

export const WaitingListAnswers = {
    COMING: 'COMING',
    NOT_INTERESTED: 'NOT_INTERESTED',
    DATE_DOES_NOT_SUIT: 'DATE_DOES_NOT_SUIT',
} as const satisfies Record<WaitingListAnswerName, WaitingListAnswerName>

/**
 * A guardian as the entry forms hold one while it is typed in: every part present, empty until
 * filled, and sent as it stands.
 */
export interface GuardianInput {
    firstname: string
    lastname: string
    email: string
    phone: string
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
    WaitingListFieldRequest
>((listId: number) => `/waiting-lists/${listId}/fields`)

const invites = createScopedCrudResource<
    WaitingListInvite
>((listId: number) => `/waiting-lists/${listId}/invites`)

const entries = createScopedCrudResource<
    WaitingListEntryWithScore,
    WaitingListEntryRequest,
    WaitingListEntryRequest,
    WaitingListEntryWithScore,
    WaitingListEntry
>((listId: number) => `/waiting-lists/${listId}/entries`)

export const listAll = lists.list
export const create = lists.create
export const getById = lists.get
export const update = lists.update
export const deleteList = lists.remove

export async function updateVisibleFields(id: number, fieldIds: number[]): Promise<WaitingList> {
    const request: WaitingListVisibleFieldsRequest = {fieldIds}
    const res = await client.put<WaitingList>(`/waiting-lists/${id}/visible-fields`, request)
    return res.data
}

export const listFields = fields.list
export const createField = fields.create
export const updateField = fields.update
export const deleteField = fields.remove

export const listInvites = invites.list
export const deleteInvite = invites.remove

export async function createInvite(listId: number, data?: WaitingListInviteRequest): Promise<WaitingListInvite> {
    const res = await client.post<WaitingListInvite>(`/waiting-lists/${listId}/invites`, data ?? {})
    return res.data
}

export const listEntries = entries.list
export const createEntry = entries.create
export const updateEntry = entries.update
export const deleteEntry = entries.remove

export async function updateCreatedAt(listId: number, entryId: number, createdAt: string): Promise<WaitingListEntry> {
    const request: WaitingListCreatedAtRequest = {createdAt}
    const res = await client.put<WaitingListEntry>(`/waiting-lists/${listId}/entries/${entryId}/created-at`, request)
    return res.data
}

export async function inviteEntry(
    listId: number,
    entryId: number,
    invitation?: WaitingListInvitationRequest | null,
): Promise<WaitingListEntry> {
    const res = await client.post<WaitingListEntry>(
        `/waiting-lists/${listId}/entries/${entryId}/invite`, invitation ?? {})
    return res.data
}

export async function returnToWaiting(listId: number, entryId: number): Promise<WaitingListEntry> {
    const res = await client.post<WaitingListEntry>(`/waiting-lists/${listId}/entries/${entryId}/back-to-waiting`)
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

export async function getInviteInfo(code: string): Promise<WaitingListInviteInfo> {
    const res = await client.get<WaitingListInviteInfo>(`/public/waiting-list/invite/${code}`)
    return res.data
}

export async function register(data: WaitingListRegisterRequest): Promise<WaitingListAccessResponse> {
    const res = await client.post<WaitingListAccessResponse>('/public/waiting-list/register', data)
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

/**
 * Answers the invitation the entry currently holds.
 *
 * The occurrence travels with the answer so it says what it answers: an entry carries one current
 * invitation, and a click from a mail that has been superseded is refused rather than applied to
 * the invitation that replaced it.
 */
export async function answerInvitation(token: string, data: WaitingListInvitationAnswerRequest): Promise<void> {
    await client.post(`/public/waiting-list/entry/${token}/answer`, data)
}

export async function approveEntry(listId: number, entryId: number): Promise<WaitingListEntry> {
    const res = await client.post<WaitingListEntry>(`/waiting-lists/${listId}/entries/${entryId}/approve`)
    return res.data
}

export async function rejectEntry(listId: number, entryId: number): Promise<void> {
    await client.post(`/waiting-lists/${listId}/entries/${entryId}/reject`)
}

export async function listPublicWaitlists(stationUid: string): Promise<PublicWaitlistSummary[]> {
    const res = await client.get<PublicWaitlistSummary[]>(`/public/station/${stationUid}/waitlists`)
    return res.data
}

export async function getPublicWaitlistForm(stationUid: string, listId: number): Promise<PublicWaitlistFormResponse> {
    const res = await client.get<PublicWaitlistFormResponse>(`/public/station/${stationUid}/waitlists/${listId}/form`)
    return res.data
}

export async function submitPublicRegistration(
    stationUid: string,
    listId: number,
    data: PublicWaitlistRegistrationRequest,
): Promise<WaitingListRegistrationStatus> {
    const res = await client.post<WaitingListRegistrationStatus>(`/public/station/${stationUid}/waitlists/${listId}/register`, data)
    return res.data
}

export async function verifyPublicRegistration(token: string): Promise<WaitingListRegistrationStatus> {
    const res = await client.get<WaitingListRegistrationStatus>(`/public/waitlist/verify/${token}`)
    return res.data
}
