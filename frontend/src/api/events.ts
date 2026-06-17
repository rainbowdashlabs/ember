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
    EventTemplate,
    EventTemplateDetail,
    EventTemplateFieldEntry,
    LayoutFieldEntry,
    MemberIdentity,
    SetEventFieldsRequest,
    StationEvent
} from './types'

// -- Events --

export interface EventListParams {
    categoryId?: number
    requiresRegistration?: boolean
}

export interface UpcomingParams {
    categoryId?: number
    requiresRegistration?: boolean
    search?: string
    limit?: number
    offset?: number
}

export interface UpcomingEventOccurrence {
    event: StationEvent
    date: string
}

export async function listUpcomingOccurrences(params?: UpcomingParams): Promise<UpcomingEventOccurrence[]> {
    const res = await client.get<UpcomingEventOccurrence[]>('/events/upcoming', { params })
    return res.data
}

export async function listEvents(params?: EventListParams): Promise<StationEvent[]> {
    const res = await client.get<StationEvent[]>('/events', { params })
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

export async function cancelEvent(eventId: number, reason?: string): Promise<void> {
    await client.post(`/events/${eventId}/cancel`, { reason: reason ?? null })
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

export async function reorderCategories(orderedIds: number[]): Promise<EventCategory[]> {
    const res = await client.put<EventCategory[]>('/events/categories/reorder', {orderedIds})
    return res.data
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
    memberIdentity?: MemberIdentity | null
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
    memberIdentity?: MemberIdentity | null
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

// -- Templates --

export async function listTemplates(): Promise<EventTemplate[]> {
    const res = await client.get<EventTemplate[]>('/event-templates')
    return res.data
}

export async function createTemplate(data: { name: string }): Promise<EventTemplate> {
    const res = await client.post<EventTemplate>('/event-templates', data)
    return res.data
}

export async function getTemplate(id: number): Promise<EventTemplateDetail> {
    const res = await client.get<EventTemplateDetail>(`/event-templates/${id}`)
    return res.data
}

export async function updateTemplate(id: number, data: {
    name?: string, title?: string | null, description?: string | null, categoryId?: number | null,
    eventType?: string | null, requiresRegistration?: boolean | null,
    registrationDeadlineOffset?: string | null, requiresConfirmation?: boolean | null,
    restrictionMode?: string | null, attendanceTemplateId?: number | null, registrationLimit?: number | null
}): Promise<void> {
    await client.put(`/event-templates/${id}`, data)
}

export async function deleteTemplate(id: number): Promise<void> {
    await client.delete(`/event-templates/${id}`)
}

export async function setTemplateFields(id: number, data: { fields: EventTemplateFieldEntry[] }): Promise<void> {
    await client.put(`/event-templates/${id}/fields`, data)
}

export async function setTemplateRestrictions(id: number, data: { userTypes: string[] }): Promise<void> {
    await client.put(`/event-templates/${id}/restrictions`, data)
}

// -- Reminders --

export async function getEventReminders(eventId: number): Promise<number[]> {
    const res = await client.get<number[]>(`/events/${eventId}/reminders`)
    return res.data
}

export async function setEventReminders(eventId: number, daysBefore: number[]): Promise<void> {
    await client.put(`/events/${eventId}/reminders`, { daysBefore })
}

export async function setTemplateReminders(templateId: number, daysBefore: number[]): Promise<void> {
    await client.put(`/event-templates/${templateId}/reminders`, { daysBefore })
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
    restrictedUserTypes?: string[]
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

// -- Registration Stats --

export interface MemberRegistrationStats {
    memberId: number
    memberName: string
    registered: number
    accepted: number
    denied: number
    declined: number
    acceptRate: number
    lastDenied?: string | null
    priority: 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE'
    fairnessScore: number
}

export async function getRegistrationStats(eventId: number, categoryId?: number, months?: number): Promise<MemberRegistrationStats[]> {
    const params: Record<string, string> = {}
    if (categoryId != null) params.categoryId = String(categoryId)
    if (months != null) params.months = String(months)
    const res = await client.get<MemberRegistrationStats[]>(`/events/${eventId}/registration-stats`, {params})
    return res.data
}

// -- Federated Events --

export interface FederatedEvent {
    partnerId: number
    partnerStationName: string
    partnerStationUid: string
    event: {
        id: number
        name: string
        description: string
        eventType: string
        dayOfWeek: number
        startTime: string
        endTime: string
        requiresRegistration: boolean
        requiresConfirmation: boolean
    }
}

export async function listFederatedEvents(): Promise<FederatedEvent[]> {
    const res = await client.get<FederatedEvent[]>('/federated/events')
    return res.data
}

export interface FederatedEventDetail {
    event: Record<string, unknown>
    publicFields: { id: number; name: string; value: string; fieldType: string; isPublic: boolean }[]
}

export async function getFederatedEvent(stationUid: string, eventId: number): Promise<FederatedEventDetail> {
    const res = await client.get<FederatedEventDetail>(`/federated/${stationUid}/events/${eventId}`)
    return res.data
}

export interface FederatedRegistration {
    eventId: number
    remoteMemberId: string
    eventDate: string
    status: string
    partnerId: number
}

export async function listMyFederatedRegistrations(): Promise<FederatedRegistration[]> {
    const res = await client.get<FederatedRegistration[]>('/federated/my-registrations')
    return res.data
}

export async function registerForFederatedEvent(stationUid: string, eventId: number, eventDate: string, memberId?: string): Promise<void> {
    await client.post(`/federated/${stationUid}/events/${eventId}/register`, { eventDate, memberId: memberId ?? null })
}

export async function withdrawFederatedRegistration(stationUid: string, eventId: number, eventDate: string, memberId?: string): Promise<void> {
    await client.delete(`/federated/${stationUid}/events/${eventId}/register`, { data: { eventDate, memberId: memberId ?? null } })
}

export interface FederatedEventRegistration {
    registration: {
        id: number
        eventId: number
        partnerId: number
        remoteMemberId: string
        eventDate: string
        status: string
        createdAt: string
    }
    memberIdentity: MemberIdentity | null
}

export async function listFederationRegistrations(eventId: number, date?: string): Promise<FederatedEventRegistration[]> {
    const params = date ? { date } : {}
    const res = await client.get<FederatedEventRegistration[]>(`/events/${eventId}/federation-registrations`, { params })
    return res.data
}

export async function updateFederationRegistrationStatus(registrationId: number, status: string): Promise<void> {
    await client.put(`/events/federation-registrations/${registrationId}/status`, { status })
}

export interface EventFederationShareInfo {
    shared: boolean
    scope?: string
    partnerIds?: number[]
}

export async function getFederationShare(eventId: number): Promise<EventFederationShareInfo> {
    const res = await client.get<EventFederationShareInfo>(`/events/${eventId}/federation`)
    return res.data
}

export async function setFederationShare(eventId: number, scope: string, partnerIds?: number[]): Promise<void> {
    await client.put(`/events/${eventId}/federation`, {scope, partnerIds: partnerIds ?? []})
}

export async function removeFederationShare(eventId: number): Promise<void> {
    await client.delete(`/events/${eventId}/federation`)
}

// -- Page-editor picker (concept §4.5). PAGE_EDIT-gated. --

export type EventPickerMode = 'FUTURE' | 'PAST' | 'ALL'

export interface EventSearchResult {
    eventUid: string
    name: string
    startTime: string | null
    categoryName: string | null
}

export async function searchEvents(
    query?: string,
    mode: EventPickerMode = 'FUTURE',
    limit = 10,
): Promise<EventSearchResult[]> {
    const params: Record<string, string | number> = {mode, limit}
    if (query) params.q = query
    const res = await client.get<EventSearchResult[]>('/events/search', {params})
    return res.data
}
