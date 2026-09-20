/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
import {documentFrom, type DocumentFile} from '@/util/documentFile'
import type {MovementPurposeName, StepActorName} from './movements'
import type {StationUserTypeName} from './types'

export interface AttendanceTemplate {
    id: number
    stationId: string
    name?: string
}

export interface TemplateRequest {
    name?: string
}

export interface AttendanceTemplateField {
    id: number
    templateId: number
    name?: string
    fieldType?: string
    config?: Record<string, unknown>
    position: number
}

export interface TemplateFieldRequest {
    name?: string
    fieldType?: string
    config?: Record<string, unknown>
    position: number
}

export interface TemplateDetail {
    id: number
    stationId: string
    name?: string
    fields?: AttendanceTemplateField[]
    groups?: TemplateGroupEntry[]
}

export interface TemplateGroupEntry {
    groupId: number
    position: number
}

export interface SetTemplateGroupsRequest {
    groups?: TemplateGroupEntry[]
}

export interface AttendanceSession {
    id: number
    templateId: number
    startTime?: string
    endTime?: string
    createdAt?: string
    eventId?: number | null
    title?: string
    /** When a manager's reopening runs out, null where the sheet's age alone decides. */
    unlockedUntil?: string | null
    /** When somebody closed the sheet by hand, null where nobody did. */
    lockedAt?: string | null
    /** What a whole presence counts as when hours are added up, null where the times decide. */
    countedMinutes?: number | null
}

export interface SessionRequest {
    startTime?: string
    endTime?: string
    eventId?: number | null
    title?: string
    countedMinutes?: number | null
    /** Whom to enter on this one sheet, left out where the template's own groups decide. */
    audience?: SessionAudience
}

/**
 * Who stands on one sheet, where the template it borrows its fields from is not the answer. The two
 * add up, and the groups keep the order they were chosen in, which the sheet is then written in.
 */
export interface SessionAudience {
    userTypes: StationUserTypeName[]
    groupIds: number[]
}

export interface AttendanceSessionField {
    sessionId: number
    fieldId: number
    value?: string
}

export interface AttendanceFieldValueEntry {
    fieldId: number
    value?: string
}

export interface SetSessionFieldsRequest {
    fields?: AttendanceFieldValueEntry[]
}

export interface SessionDetail {
    session?: AttendanceSession
    fields?: AttendanceSessionField[]
    entries?: AttendanceEntry[]
    /** Whether the sheet refuses writes; decided by the backend, which owns the span. */
    locked?: boolean
}

export type AttendanceStatus = 'UNCONFIRMED' | 'PRESENT' | 'ABSENT' | 'DECLINED'

export type EntrySource = 'EXPECTED' | 'EXTRA'

export interface AttendanceEntry {
    id: number
    sessionId: number
    memberId: number
    status: AttendanceStatus
    checkIn?: string
    checkOut?: string
    source: EntrySource
}

export interface CreateEntryRequest {
    memberId?: number
    source?: EntrySource
}

export interface TimestampRequest {
    time?: string
}

export interface TimestampResponse {
    entryId: number
    time?: string
}

const templates = createCrudResource<
    AttendanceTemplate,
    TemplateRequest,
    TemplateRequest,
    TemplateDetail
>('/attendance/templates')

const templateFields = createScopedCrudResource<
    AttendanceTemplateField,
    TemplateFieldRequest,
    TemplateFieldRequest,
    AttendanceTemplateField,
    AttendanceTemplateField[]
>((templateId: number) => `/attendance/templates/${templateId}/fields`)

// -- Templates --

export const listTemplates = templates.list
export const getTemplate = templates.get

/** Every template of the station with its fields and its groups, in one call rather than one a tile. */
export async function listTemplateDetails(): Promise<TemplateDetail[]> {
    const res = await client.get<TemplateDetail[]>('/attendance/templates/detail')
    return res.data
}

export const createTemplate = templates.create
export const updateTemplate = templates.update
export const deleteTemplate = templates.remove

// -- Template Fields --

export const listTemplateFields = templateFields.list
export const createTemplateField = templateFields.create
export const updateTemplateField = templateFields.update

export async function deleteTemplateField(templateId: number, fieldId: number): Promise<AttendanceTemplateField[]> {
    const res = await client.delete<AttendanceTemplateField[]>(`/attendance/templates/${templateId}/fields/${fieldId}`)
    return res.data
}

// -- Template Groups --

export async function setTemplateGroups(templateId: number, data: SetTemplateGroupsRequest): Promise<TemplateGroupEntry[]> {
    const res = await client.put<TemplateGroupEntry[]>(`/attendance/templates/${templateId}/groups`, data)
    return res.data
}

// -- Sessions --

export interface SessionSummary {
    id: number
    templateId: number
    startTime?: string
    endTime?: string
    createdAt?: string
    eventId?: number | null
    title?: string
    presentCount: number
    absentCount: number
    declinedCount: number
    unconfirmedCount: number
}

const sessions = createCrudResource<
    SessionSummary,
    SessionRequest,
    SessionRequest,
    SessionDetail,
    AttendanceSession
>('/attendance/sessions')

const templateSessions = createScopedCrudResource<
    AttendanceSession,
    SessionRequest
>((templateId: number) => `/attendance/templates/${templateId}/sessions`)

export const listSessionSummaries = sessions.list
export const listSessions = templateSessions.list
export const getSession = sessions.get
export const createSession = templateSessions.create
export const updateSession = sessions.update
export const deleteSession = sessions.remove

// -- Session Fields --

export async function getSessionFields(sessionId: number): Promise<AttendanceSessionField[]> {
    const res = await client.get<AttendanceSessionField[]>(`/attendance/sessions/${sessionId}/fields`)
    return res.data
}

export async function setSessionFields(sessionId: number, data: SetSessionFieldsRequest): Promise<AttendanceSessionField[]> {
    const res = await client.put<AttendanceSessionField[]>(`/attendance/sessions/${sessionId}/fields`, data)
    return res.data
}

// -- Entries --

const sessionEntries = createScopedCrudResource<
    AttendanceEntry,
    CreateEntryRequest,
    CreateEntryRequest,
    AttendanceEntry,
    AttendanceEntry[]
>((sessionId: number) => `/attendance/sessions/${sessionId}/entries`)

const entries = createCrudResource<AttendanceEntry>('/attendance/entries')

export const listEntries = sessionEntries.list
export const createEntry = sessionEntries.create
export const deleteEntry = entries.remove

export async function checkIn(entryId: number, data: TimestampRequest): Promise<TimestampResponse> {
    const res = await client.post<TimestampResponse>(`/attendance/entries/${entryId}/check-in`, data)
    return res.data
}

export async function checkOut(entryId: number, data: TimestampRequest): Promise<TimestampResponse> {
    const res = await client.post<TimestampResponse>(`/attendance/entries/${entryId}/check-out`, data)
    return res.data
}

export async function resetTimes(entryId: number): Promise<void> {
    await client.post(`/attendance/entries/${entryId}/reset-times`)
}

export async function updateEntryStatus(entryId: number, status: string): Promise<{ entryId: number; status: string }> {
    const res = await client.put<{ entryId: number; status: string }>(`/attendance/entries/${entryId}/status`, {status})
    return res.data
}

export async function syncFromEvent(sessionId: number): Promise<AttendanceEntry[]> {
    const res = await client.post<AttendanceEntry[]>(`/attendance/sessions/${sessionId}/sync-event`)
    return res.data
}

/** A swap of the member's that has not finished. */
export interface SwapNote {
    movementId: number
    /** What it is for, which is what says whether a piece is coming or going. */
    purpose: MovementPurposeName
    /** The step it stands on, which is what acknowledging it names. */
    stepId: number | null
    /** The words that step carries, in the chain's own wording. */
    stepLabel: string
    /** Whose turn it is. */
    stepActor: StepActorName | null
    /** Whether acknowledging it puts a piece into the member's hands. */
    handOverNext: boolean
    /** The piece set aside for the member, which the step that hands it over has to be told about. */
    replacementItemId: number | null
    inventoryName: string
    /** The piece this step is about: the one arriving where it brings one, the one held otherwise. */
    itemName: string
    /** The size written on that piece, absent where its inventory keeps no sizes. */
    itemSize: string | null
}

/** A found item the member claimed and has not collected. */
export interface FoundNote {
    itemId: number
    description: string
}

/**
 * What is outstanding for one member. Absent entirely where they have nothing, and carrying only
 * what the reader is allowed to see: the server leaves the rest out rather than sending it.
 */
export interface MemberNotes {
    memberId: number
    swaps: SwapNote[]
    foundItems: FoundNote[]
    /** How many days ago their birthday fell, zero for today, null where there is none to show. */
    birthdayDaysAgo: number | null
}

export async function getMemberNotes(sessionId: number): Promise<MemberNotes[]> {
    const res = await client.get<MemberNotes[]>(`/attendance/sessions/${sessionId}/member-notes`)
    return res.data
}

export async function unlockSession(sessionId: number): Promise<AttendanceSession> {
    const res = await client.post<AttendanceSession>(`/attendance/sessions/${sessionId}/unlock`)
    return res.data
}

export async function lockSession(sessionId: number): Promise<AttendanceSession> {
    const res = await client.post<AttendanceSession>(`/attendance/sessions/${sessionId}/lock`)
    return res.data
}

/**
 * How the sheet is printed, beyond the attendance itself.
 *
 * <p>Everything is optional and an export that asks for nothing is the one the product has always
 * produced: the recorded attendance, with the address of the instance at the foot of the page.
 */
export interface SheetOptions {
    /** Whether every line ends in a box to sign, which drops the recorded status from the sheet. */
    signature?: boolean
    /** The heading, the session's own where it is absent, and a line to write on where it is empty. */
    title?: string
    /** Empty numbered lines after the last person, for whoever turns up without being on the list. */
    blankRows?: number
    /** Whether the address of this installation is printed, the station's own setting where absent. */
    instanceUrl?: boolean
}

export async function exportPdf(sessionId: number, options: SheetOptions = {}): Promise<DocumentFile> {
    const params: Record<string, string> = {}
    if (options.signature) params.signature = 'true'
    if (options.title !== undefined) params.title = options.title
    if (options.blankRows) params.blankRows = String(options.blankRows)
    if (options.instanceUrl !== undefined) params.instanceUrl = String(options.instanceUrl)
    const res = await client.get(`/attendance/sessions/${sessionId}/export`, {params, responseType: 'blob'})
    return documentFrom(res, 'Anwesenheitsliste.pdf')
}

// -- Report --

export interface MemberSummary {
    memberId: number
    name: string
    totalHours: number
    sessionCount: number
    presentCount: number
}

export interface SessionMemberEntry {
    memberId: number
    name: string
    status: string
    /** When the member arrived, with its day, since a sheet may run over several. */
    checkIn: string
    checkOut: string
    hours: number
}

export interface SessionData {
    sessionId: number
    title: string
    date: string
    /** The day the sheet ends on, which is the day it starts on for all but a camp. */
    endDate?: string | null
    startTime: string
    endTime: string
    expectedCount: number
    presentCount: number
    /** What a whole presence was worth, null where the sheet's times decided. */
    countedHours?: number | null
    entries: SessionMemberEntry[]
}

export interface MonthSummary {
    month: string
    members: MemberSummary[]
    sessions: SessionData[]
}

export interface ReportData {
    filterLabel: string
    members: MemberSummary[]
    sessions: SessionData[]
    monthlySummaries: MonthSummary[]
}

export interface ReportPreset {
    id: number
    stationId: string
    name: string
    roleName?: string
    groupId?: number | null
    period: string
    rounding: string
}

export async function reportPreview(params: URLSearchParams): Promise<ReportData> {
    const res = await client.get<ReportData>('/attendance/report/preview', {params})
    return res.data
}

export async function reportExport(params: URLSearchParams): Promise<DocumentFile> {
    const res = await client.get('/attendance/report/export', {params, responseType: 'blob'})
    return documentFrom(res, 'Anwesenheit.pdf')
}

/** The same summary as a spreadsheet, for a reader who has to add the hours up elsewhere. */
export async function reportExportCsv(params: URLSearchParams): Promise<DocumentFile> {
    const res = await client.get('/attendance/report/export.csv', {params, responseType: 'blob'})
    return documentFrom(res, 'Anwesenheit.csv')
}

interface PresetRequest {
    name: string
    roleName?: string
    groupId?: number | null
    period: string
    rounding: string
}

const presets = createCrudResource<ReportPreset, PresetRequest>('/attendance/report/presets')

export const listPresets = presets.list
export const createPreset = presets.create
export const deletePreset = presets.remove
