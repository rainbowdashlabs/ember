/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
import {documentFrom, type DocumentFile} from '@/util/documentFile'
import type {
    AttendanceEntry,
    AttendanceReportPreset,
    AttendanceSession,
    AttendanceSessionField,
    AttendanceTemplate,
    AttendanceTemplateField,
    components,
    CreateEntryRequest,
    CreatePresetRequest,
    MemberNotes,
    ReportData,
    SessionDetail,
    SessionRequest,
    SessionSummary,
    SetSessionFieldsRequest,
    SetTemplateGroupsRequest,
    SetTemplateUserTypesRequest,
    StationUserType,
    StatusResponse,
    TemplateDetail,
    TemplateFieldRequest,
    TemplateGroupEntry,
    TemplateRequest,
    TimestampRequest,
    TimestampResponse,
} from './generated/schema'

export type AttendanceStatus = components['schemas']['AttendanceStatus']

export type EntrySource = components['schemas']['EntrySource']

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

export const listTemplateFields = templateFields.list
export const createTemplateField = templateFields.create
export const updateTemplateField = templateFields.update

export async function deleteTemplateField(templateId: number, fieldId: number): Promise<AttendanceTemplateField[]> {
    const res = await client.delete<AttendanceTemplateField[]>(`/attendance/templates/${templateId}/fields/${fieldId}`)
    return res.data
}

export async function setTemplateGroups(templateId: number, data: SetTemplateGroupsRequest): Promise<TemplateGroupEntry[]> {
    const res = await client.put<TemplateGroupEntry[]>(`/attendance/templates/${templateId}/groups`, data)
    return res.data
}

export async function setTemplateUserTypes(
    templateId: number,
    data: SetTemplateUserTypesRequest,
): Promise<StationUserType[]> {
    const res = await client.put<StationUserType[]>(`/attendance/templates/${templateId}/user-types`, data)
    return res.data
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

/**
 * The sheet an appointment has on one of its days, or null where nobody has taken it yet.
 *
 * <p>A repeating appointment has one sheet per day it comes round, so the day is asked for; left
 * out, it is the station's today.
 */
export async function getSessionForEvent(eventId: number, date?: string | null): Promise<AttendanceSession | null> {
    const res = await client.get<AttendanceSession | ''>(`/attendance/events/${eventId}/session`, {
        params: date ? {date} : undefined,
    })
    return res.status === 204 || !res.data ? null : res.data
}

export async function getSessionFields(sessionId: number): Promise<AttendanceSessionField[]> {
    const res = await client.get<AttendanceSessionField[]>(`/attendance/sessions/${sessionId}/fields`)
    return res.data
}

export async function setSessionFields(sessionId: number, data: SetSessionFieldsRequest): Promise<AttendanceSessionField[]> {
    const res = await client.put<AttendanceSessionField[]>(`/attendance/sessions/${sessionId}/fields`, data)
    return res.data
}

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

export async function updateEntryStatus(entryId: number, status: AttendanceStatus): Promise<StatusResponse> {
    const res = await client.put<StatusResponse>(`/attendance/entries/${entryId}/status`, {status})
    return res.data
}

export async function syncFromEvent(sessionId: number): Promise<AttendanceEntry[]> {
    const res = await client.post<AttendanceEntry[]>(`/attendance/sessions/${sessionId}/sync-event`)
    return res.data
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

const presets = createCrudResource<AttendanceReportPreset, CreatePresetRequest>('/attendance/report/presets')

export const listPresets = presets.list
export const createPreset = presets.create
export const deletePreset = presets.remove
