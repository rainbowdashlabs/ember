/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    AllEventRestrictions,
    BreakRequest,
    CategoryRequest,
    EventBreak,
    EventCategory,
    EventField,
    EventLayout,
    EventLayoutField,
    EventRequest,
    EventRestrictions,
    LayoutFieldEntry,
    SetEventFieldsRequest,
    StationEvent
} from './types'

// -- Events --

export async function listEvents(): Promise<StationEvent[]> {
    const res = await client.get<StationEvent[]>('/events')
    return res.data
}

export async function listTodayEvents(): Promise<StationEvent[]> {
    const res = await client.get<StationEvent[]>('/events/today')
    return res.data
}

export async function getEvent(id: number): Promise<StationEvent> {
    const res = await client.get<StationEvent>(`/events/${id}`)
    return res.data
}

export async function createEvent(data: EventRequest): Promise<StationEvent> {
    const res = await client.post<StationEvent>('/events', data)
    return res.data
}

export async function updateEvent(id: number, data: EventRequest): Promise<StationEvent> {
    const res = await client.put<StationEvent>(`/events/${id}`, data)
    return res.data
}

export async function deleteEvent(id: number): Promise<void> {
    await client.delete(`/events/${id}`)
}

// -- Categories --

export async function listCategories(): Promise<EventCategory[]> {
    const res = await client.get<EventCategory[]>('/events/categories')
    return res.data
}

export async function createCategory(data: CategoryRequest): Promise<EventCategory> {
    const res = await client.post<EventCategory>('/events/categories', data)
    return res.data
}

export async function updateCategory(id: number, data: CategoryRequest): Promise<unknown> {
    const res = await client.put(`/events/categories/${id}`, data)
    return res.data
}

export async function deleteCategory(id: number): Promise<void> {
    await client.delete(`/events/categories/${id}`)
}

// -- Registrations --

export interface EventRegistrationEntry {
    id: number
    eventId: number
    memberId: number
    memberName: string
    eventDate: string
    status: string  // RegistrationStatusName
    createdAt: string
    createdByName?: string | null
}

export async function listMyRegistrations(): Promise<EventRegistrationEntry[]> {
    const res = await client.get<EventRegistrationEntry[]>('/events/registrations/mine')
    return res.data
}

export async function listPendingRegistrations(): Promise<EventRegistrationEntry[]> {
    const res = await client.get<EventRegistrationEntry[]>('/events/registrations/pending')
    return res.data
}

export async function listEventRegistrations(eventId: number, date?: string): Promise<EventRegistrationEntry[]> {
    const params = date ? { date } : undefined
    const res = await client.get<EventRegistrationEntry[]>(`/events/${eventId}/registrations`, { params })
    return res.data
}

export interface AbsentMember {
    memberId: number
    memberName: string
    absentFrom: string
    absentUntil: string
    reason?: string | null
}

export async function listAbsencesForDate(eventId: number, date: string): Promise<AbsentMember[]> {
    const res = await client.get<AbsentMember[]>(`/events/${eventId}/absences`, { params: { date } })
    return res.data
}

export async function getRestrictions(eventId: number): Promise<EventRestrictions> {
    const res = await client.get<EventRestrictions>(`/events/${eventId}/restrictions`)
    return res.data
}

export async function setRestrictions(eventId: number, data: EventRestrictions): Promise<EventRestrictions> {
    const res = await client.put<EventRestrictions>(`/events/${eventId}/restrictions`, data)
    return res.data
}

export async function listAllRestrictions(): Promise<AllEventRestrictions> {
    const res = await client.get<AllEventRestrictions>('/events/restrictions')
    return res.data
}

/**
 * Returns a map of eventId -> eligible memberIds (self + managed).
 * Events without restrictions are omitted (all eligible).
 */
export async function listEligibleMembers(): Promise<Record<number, number[]>> {
    const res = await client.get<Record<number, number[]>>('/events/eligible-members')
    return res.data
}

export async function registerForEvent(eventId: number, data: {
    eventDate?: string;
    memberId?: number
}): Promise<unknown> {
    const res = await client.post(`/events/${eventId}/register`, data)
    return res.data
}

export async function declineEvent(eventId: number, data: { eventDate?: string; memberId?: number }): Promise<unknown> {
    const res = await client.post(`/events/${eventId}/decline`, data)
    return res.data
}

export interface RegistrationCount {
    eventId: number
    eventDate: string
    status: string
    count: number
}

export async function listRegistrationCounts(): Promise<RegistrationCount[]> {
    const res = await client.get<RegistrationCount[]>('/events/registrations/counts')
    return res.data
}

export async function withdrawRegistration(id: number): Promise<void> {
    await client.delete(`/events/registrations/${id}`)
}

export async function updateRegistrationStatus(id: number, status: string): Promise<unknown> {
    const res = await client.put(`/events/registrations/${id}/status`, {status})
    return res.data
}

// -- Breaks --

// -- Field Defaults --

export interface EventFieldDefault {
    eventId: number
    fieldId: number
    source: string
    value?: string
}

export interface FieldDefaultEntry {
    fieldId: number
    source: string
    value?: string
}

export async function getFieldDefaults(eventId: number): Promise<EventFieldDefault[]> {
    const res = await client.get<EventFieldDefault[]>(`/events/${eventId}/field-defaults`)
    return res.data
}

export async function setFieldDefaults(eventId: number, data: FieldDefaultEntry[]): Promise<EventFieldDefault[]> {
    const res = await client.put<EventFieldDefault[]>(`/events/${eventId}/field-defaults`, data)
    return res.data
}

// -- Breaks --

export async function listBreaks(): Promise<EventBreak[]> {
    const res = await client.get<EventBreak[]>('/events/breaks')
    return res.data
}

export async function createBreak(data: BreakRequest): Promise<EventBreak> {
    const res = await client.post<EventBreak>('/events/breaks', data)
    return res.data
}

export async function updateBreak(id: number, data: BreakRequest): Promise<EventBreak> {
    const res = await client.put<EventBreak>(`/events/breaks/${id}`, data)
    return res.data
}

export async function deleteBreak(id: number): Promise<void> {
    await client.delete(`/events/breaks/${id}`)
}

export async function listFieldNames(): Promise<string[]> {
    const res = await client.get<string[]>('/events/field-names')
    return res.data
}

// -- Export --

export async function exportEventList(data: {
    categoryIds?: number[]
    columns?: { type: string; key?: string; fieldName?: string; label: string }[]
    from: string
    to: string
}): Promise<Blob> {
    const res = await client.post('/events/export', data, {responseType: 'blob'})
    return res.data as Blob
}

// -- Event Fields (per-event) --

export async function getEventFields(eventId: number): Promise<EventField[]> {
    const res = await client.get<EventField[]>(`/events/${eventId}/fields`)
    return res.data
}

export async function setEventFields(eventId: number, data: SetEventFieldsRequest): Promise<EventField[]> {
    const res = await client.put<EventField[]>(`/events/${eventId}/fields`, data)
    return res.data
}

// -- Overview Fields --

export async function getOverviewFields(): Promise<Record<number, EventField[]>> {
    const res = await client.get<Record<number, EventField[]>>('/events/overview-fields')
    return res.data
}

// -- Layouts --

export async function listLayouts(): Promise<EventLayout[]> {
    const res = await client.get<EventLayout[]>('/events/layouts')
    return res.data
}

export async function createLayout(data: { name: string }): Promise<EventLayout> {
    const res = await client.post<EventLayout>('/events/layouts', data)
    return res.data
}

export async function getLayout(id: number): Promise<EventLayout> {
    const res = await client.get<EventLayout>(`/events/layouts/${id}`)
    return res.data
}

export async function updateLayout(id: number, data: { name: string }): Promise<EventLayout> {
    const res = await client.put<EventLayout>(`/events/layouts/${id}`, data)
    return res.data
}

export async function deleteLayout(id: number): Promise<void> {
    await client.delete(`/events/layouts/${id}`)
}

export async function getLayoutFields(layoutId: number): Promise<EventLayoutField[]> {
    const res = await client.get<EventLayoutField[]>(`/events/layouts/${layoutId}/fields`)
    return res.data
}

export async function setLayoutFields(layoutId: number, data: { fields: LayoutFieldEntry[] }): Promise<EventLayoutField[]> {
    const res = await client.put<EventLayoutField[]>(`/events/layouts/${layoutId}/fields`, data)
    return res.data
}

// -- Batch Creation --

export interface BatchRow {
    name?: string
    startTime: string
    endTime: string
    fieldValues?: Record<string, string>
}

export interface BatchCreateRequest {
    name?: string
    description?: string
    templateId?: number | null
    categoryId?: number | null
    layoutId?: number | null
    inlineFields?: LayoutFieldEntry[]
    rows: BatchRow[]
    requiresRegistration?: boolean
    requiresConfirmation?: boolean
    registrationDeadline?: string | null
    restrictedRoleIds?: number[]
    restrictedGroupIds?: number[]
    restrictedTagIds?: number[]
}

export interface GenerateDatesRequest {
    intervalType: string
    dayOfWeek?: number
    startDate: string
    endDate: string
    startTime?: string
    endTime?: string
    ignoreBreaks?: boolean
}

export async function generateDates(data: GenerateDatesRequest): Promise<BatchRow[]> {
    const res = await client.post<BatchRow[]>('/events/batch/generate-dates', data)
    return res.data
}

export async function createBatchEvents(data: BatchCreateRequest): Promise<StationEvent[]> {
    const res = await client.post<StationEvent[]>('/events/batch', data)
    return res.data
}
