/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
export const WaitingListEntryStatus = {
    PENDING: 'PENDING',
    WAITING: 'WAITING',
    INVITED: 'INVITED',
    TESTING: 'TESTING',
    WITHDRAWN: 'WITHDRAWN',
    JOINED: 'JOINED',
} as const

export type WaitingListEntryStatusName = (typeof WaitingListEntryStatus)[keyof typeof WaitingListEntryStatus]

export const WaitingListFieldTypes = {
    TEXT: 'TEXT',
    NUMBER: 'NUMBER',
    DATE: 'DATE',
    BOOLEAN: 'BOOLEAN',
    ENUM: 'ENUM',
    /**
     * A date field carrying the date of birth. A list has at most one, which is what lets it work
     * out an age without being told where to look. Stored exactly like a DATE field, so an ordinary
     * date field becomes one without losing the answers already given.
     */
    BIRTH_DATE: 'BIRTH_DATE',
} as const

export type WaitingListFieldTypeName = (typeof WaitingListFieldTypes)[keyof typeof WaitingListFieldTypes]

export interface WaitingList {
    id: number
    stationId: string
    name: string
    description: string
    scoringFormula?: string | null
    confirmIntervalDays: number
    createdAt: string
    visibleFields: number[]
    testingGroupId?: number | null
    joinGroupId?: number | null
    attendanceThreshold: number
    isPublic: boolean
    /** How old somebody must be to put themselves on the list; null for no limit. */
    minAgeRegister?: number | null
    /** How old they must be to join from it. Younger entries are marked rather than hidden. */
    minAgeJoin?: number | null
}

/**
 * What a field carries beyond its type: the choices of an ENUM, the hint of a text field.
 *
 * An object in both directions, so nothing ever parses it.
 */
export interface WaitingListFieldConfig {
    options?: string[]
    placeholder?: string
}

export interface WaitingListField {
    id: number
    listId: number
    name: string
    fieldType: WaitingListFieldTypeName
    config: WaitingListFieldConfig
    position: number
    required: boolean
    isPublic: boolean
}

export interface WaitingListInvite {
    id: number
    listId: number
    code: string
    maxUses: number
    uses: number
    expiresAt?: string | null
    createdAt: string
}

/**
 * The one appointment an entry has been invited to, or null while nobody has been invited.
 *
 * Nobody is signed up from it: they have not joined anything, so they are on no attendee list and
 * count towards no total the station plans from.
 */
export interface WaitingListInvitation {
    eventId: number
    date: string
    /** When they were asked to be there, usually earlier than everybody else. */
    arrivalTime?: string | null
}

/** What the station sends when it invites: the evening, or nothing to invite without one. */
export interface WaitingListInvitationRequest {
    eventId: number
    date: string
    arrivalTime?: string | null
}

export interface WaitingListEntry {
    id: number
    listId: number
    firstname: string
    lastname: string
    parentName: string
    email: string
    accessToken: string
    status: WaitingListEntryStatusName
    confirmedAt: string
    reminderSentAt?: string | null
    createdAt: string
    notes: string
    memberId?: number | null
    invitedAt?: string | null
    testingAt?: string | null
    joinedAt?: string | null
    withdrawnAt?: string | null
    attendanceCount: number
    invitation?: WaitingListInvitation | null
    answer?: WaitingListInvitationAnswer | null
}

export interface WaitingListEntryValue {
    entryId: number
    fieldId: number
    value: unknown
}

export interface WaitingListEntryGuardian {
    id: number
    entryId: number
    firstname: string
    lastname: string
    email: string
    phone: string
    position: number
}

export interface WaitingListEntryWithScore {
    entry: WaitingListEntry
    values: WaitingListEntryValue[]
    score: number
    guardians: WaitingListEntryGuardian[]
    /** How old they are today, or null when the list has no birth date field or it went unanswered. */
    age?: number | null
    /** Whether they are waiting for their age rather than for their turn. */
    belowJoinAge: boolean
}

export interface WaitingListWithCount {
    list: WaitingList
    entryCount: number
}

export const WaitingListAnswers = {
    COMING: 'COMING',
    NOT_INTERESTED: 'NOT_INTERESTED',
    DATE_DOES_NOT_SUIT: 'DATE_DOES_NOT_SUIT',
} as const

export type WaitingListAnswerName = (typeof WaitingListAnswers)[keyof typeof WaitingListAnswers]

/** What came back to the invitation an entry currently holds, or null while nothing has. */
export interface WaitingListInvitationAnswer {
    answer: WaitingListAnswerName
    answeredAt: string
    note: string
}

/** The evening the entry is invited to, already written out the way the mail wrote it. */
export interface WaitingListPublicInvitation {
    eventId: number
    date: string
    appointmentName: string
    appointmentDate: string
    appointmentTime: string
    arrivalTime: string
    location: string
}

export interface WaitingListPublicStatus {
    firstname: string
    lastname: string
    parentName: string
    email: string
    status: string
    confirmedAt: string
    createdAt: string
    confirmIntervalDays: number
    position: number
    listName: string
    fields: WaitingListField[]
    values: WaitingListEntryValue[]
    guardians: WaitingListEntryGuardian[]
    invitation?: WaitingListPublicInvitation | null
    answer?: WaitingListInvitationAnswer | null
}

export interface WaitingListInviteInfo {
    listName: string
    listDescription: string
    fields: WaitingListField[]
}

export interface GuardianInput {
    firstname: string
    lastname: string
    email: string
    phone: string
}

export interface PublicWaitlistSummary {
    id: number
    name: string
    description: string
}

export interface PublicWaitlistFormResponse {
    listName: string
    listDescription: string
    fields: WaitingListField[]
}

interface WaitingListRequest {
    name: string
    description?: string
    scoringFormula?: string | null
    confirmIntervalDays?: number
    testingGroupId?: number | null
    joinGroupId?: number | null
    attendanceThreshold?: number
    isPublic?: boolean
    minAgeRegister?: number | null
    minAgeJoin?: number | null
}

interface FieldRequest {
    name: string
    fieldType: string
    config?: WaitingListFieldConfig
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

/**
 * Answers the invitation the entry currently holds.
 *
 * The evening travels with the answer so it says what it answers: an entry carries one current
 * invitation, and a click from a mail that has been superseded is refused rather than applied to
 * the invitation that replaced it.
 */
export async function answerInvitation(token: string, data: {
    eventId?: number | null
    date?: string | null
    answer: WaitingListAnswerName
    note?: string
}): Promise<void> {
    await client.post(`/public/waiting-list/entry/${token}/answer`, data)
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
