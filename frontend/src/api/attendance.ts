/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
import type {
    AttendanceEntry,
    AttendanceSession,
    AttendanceSessionField,
    AttendanceTemplate,
    AttendanceTemplateField,
    CreateEntryRequest,
    SessionDetail,
    SessionRequest,
    SetSessionFieldsRequest,
    SetTemplateGroupsRequest,
    TemplateDetail,
    TemplateFieldRequest,
    TemplateGroupEntry,
    TemplateRequest,
    TimestampRequest,
    TimestampResponse,
} from './types'

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

export async function exportPdf(sessionId: number): Promise<Blob> {
    const res = await client.get(`/attendance/sessions/${sessionId}/export`, {responseType: 'blob'})
    return res.data as Blob
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
    checkIn: string
    checkOut: string
    hours: number
}

export interface SessionData {
    sessionId: number
    title: string
    date: string
    startTime: string
    endTime: string
    expectedCount: number
    presentCount: number
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

export async function reportExport(params: URLSearchParams): Promise<Blob> {
    const res = await client.get('/attendance/report/export', {params, responseType: 'blob'})
    return res.data as Blob
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
