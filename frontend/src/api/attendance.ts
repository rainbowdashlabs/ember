/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
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

// -- Templates --

export async function listTemplates(): Promise<AttendanceTemplate[]> {
    const res = await client.get<AttendanceTemplate[]>('/attendance/templates')
    return res.data
}

export async function getTemplate(id: number): Promise<TemplateDetail> {
    const res = await client.get<TemplateDetail>(`/attendance/templates/${id}`)
    return res.data
}

export async function createTemplate(data: TemplateRequest): Promise<AttendanceTemplate> {
    const res = await client.post<AttendanceTemplate>('/attendance/templates', data)
    return res.data
}

export async function updateTemplate(id: number, data: TemplateRequest): Promise<AttendanceTemplate> {
    const res = await client.put<AttendanceTemplate>(`/attendance/templates/${id}`, data)
    return res.data
}

export async function deleteTemplate(id: number): Promise<void> {
    await client.delete(`/attendance/templates/${id}`)
}

// -- Template Fields --

export async function listTemplateFields(templateId: number): Promise<AttendanceTemplateField[]> {
    const res = await client.get<AttendanceTemplateField[]>(`/attendance/templates/${templateId}/fields`)
    return res.data
}

export async function createTemplateField(templateId: number, data: TemplateFieldRequest): Promise<AttendanceTemplateField[]> {
    const res = await client.post<AttendanceTemplateField[]>(`/attendance/templates/${templateId}/fields`, data)
    return res.data
}

export async function updateTemplateField(templateId: number, fieldId: number, data: TemplateFieldRequest): Promise<AttendanceTemplateField[]> {
    const res = await client.put<AttendanceTemplateField[]>(`/attendance/templates/${templateId}/fields/${fieldId}`, data)
    return res.data
}

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

export async function listSessionSummaries(): Promise<SessionSummary[]> {
    const res = await client.get<SessionSummary[]>('/attendance/sessions')
    return res.data
}

export async function listSessions(templateId: number): Promise<AttendanceSession[]> {
    const res = await client.get<AttendanceSession[]>(`/attendance/templates/${templateId}/sessions`)
    return res.data
}

export async function getSession(id: number): Promise<SessionDetail> {
    const res = await client.get<SessionDetail>(`/attendance/sessions/${id}`)
    return res.data
}

export async function createSession(templateId: number, data: SessionRequest): Promise<AttendanceSession> {
    const res = await client.post<AttendanceSession>(`/attendance/templates/${templateId}/sessions`, data)
    return res.data
}

export async function updateSession(id: number, data: SessionRequest): Promise<AttendanceSession> {
    const res = await client.put<AttendanceSession>(`/attendance/sessions/${id}`, data)
    return res.data
}

export async function deleteSession(id: number): Promise<void> {
    await client.delete(`/attendance/sessions/${id}`)
}

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

export async function listEntries(sessionId: number): Promise<AttendanceEntry[]> {
    const res = await client.get<AttendanceEntry[]>(`/attendance/sessions/${sessionId}/entries`)
    return res.data
}

export async function createEntry(sessionId: number, data: CreateEntryRequest): Promise<AttendanceEntry[]> {
    const res = await client.post<AttendanceEntry[]>(`/attendance/sessions/${sessionId}/entries`, data)
    return res.data
}

export async function deleteEntry(id: number): Promise<void> {
    await client.delete(`/attendance/entries/${id}`)
}

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
    stationId: number
    name: string
    roleName?: string
    groupId?: number | null
    period: string
    rounding: string
}

export async function reportPreview(params: {
    role?: string;
    groupId?: number;
    from: string;
    to: string;
    rounding: string
}): Promise<ReportData> {
    const res = await client.get<ReportData>('/attendance/report/preview', {params})
    return res.data
}

export async function reportExport(params: {
    role?: string;
    groupId?: number;
    from: string;
    to: string;
    rounding: string
}): Promise<Blob> {
    const res = await client.get('/attendance/report/export', {params, responseType: 'blob'})
    return res.data as Blob
}

export async function listPresets(): Promise<ReportPreset[]> {
    const res = await client.get<ReportPreset[]>('/attendance/report/presets')
    return res.data
}

export async function createPreset(data: {
    name: string;
    roleName?: string;
    groupId?: number | null;
    period: string;
    rounding: string
}): Promise<ReportPreset> {
    const res = await client.post<ReportPreset>('/attendance/report/presets', data)
    return res.data
}

export async function deletePreset(id: number): Promise<void> {
    await client.delete(`/attendance/report/presets/${id}`)
}
