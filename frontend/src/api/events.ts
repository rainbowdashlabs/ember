/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {MemberIdentity, RestrictionSelection} from './types'

export interface EventCategory {
    id: number
    stationId: string
    name?: string
    position: number
    maxShownEvents?: number | null
    isPublic?: boolean
    registrationLimit?: number | null
    color?: string | null
}

export interface CategoryRequest {
    name?: string
    position: number
    maxShownEvents?: number | null
    isPublic?: boolean
    registrationLimit?: number | null
    color?: string | null
}

export interface StationEvent {
    id: number
    stationId: string
    name?: string
    description?: string
    eventType?: string
    dayOfWeek?: number | null
    startTime?: string
    endTime?: string
    templateId?: number | null
    requiresRegistration?: boolean
    registrationDeadline?: string | null
    requiresConfirmation?: boolean
    categoryId?: number | null
    restrictionMode?: string
    restricted?: boolean
    isPublic?: boolean
    registrationLimit?: number | null
    cancelled?: boolean
    cancelledAt?: string | null
    cancelReason?: string | null
    minRegistrations?: number | null
    thresholdDate?: string | null
    thresholdNotified?: boolean
    registrationCloseDays?: number | null
}

export interface EventRequest {
    name?: string
    description?: string
    eventType?: string
    dayOfWeek?: number | null
    startTime?: string
    endTime?: string
    templateId?: number | null
    requiresRegistration?: boolean
    registrationDeadline?: string | null
    requiresConfirmation?: boolean
    categoryId?: number | null
    restriction?: RestrictionSelection
    isPublic?: boolean
    registrationLimit?: number | null
    minRegistrations?: number | null
    thresholdDate?: string | null
    registrationCloseDays?: number | null
}

export interface EventRestrictions {
    userTypes: string[]
    groupIds: number[]
    tagIds: number[]
    memberIds: number[]
    mode?: string
}

export interface AllEventRestrictions {
    [eventId: number]: EventRestrictions
}

export interface EventBreak {
    id: number
    stationId: string
    name?: string
    startDate?: string
    endDate?: string
}

export interface BreakRequest {
    name?: string
    startDate?: string
    endDate?: string
}

export const EventFieldTypes = {
    STRING: 'STRING',
    NUMBER: 'NUMBER',
    TIME: 'TIME',
    DATE: 'DATE',
    BOOLEAN: 'BOOLEAN',
    ENUM: 'ENUM',
    URL: 'URL',
    TEXTAREA: 'TEXTAREA',
    LOCATION: 'LOCATION',
    MEMBER: 'MEMBER',
    MEMBER_LIST: 'MEMBER_LIST',
    MEMBER_OF_GROUP: 'MEMBER_OF_GROUP',
    MEMBER_LIST_OF_GROUP: 'MEMBER_LIST_OF_GROUP',
    MEMBER_OF_TYPE: 'MEMBER_OF_TYPE',
    MEMBER_LIST_OF_TYPE: 'MEMBER_LIST_OF_TYPE',
    MEMBER_OF_TAG: 'MEMBER_OF_TAG',
    MEMBER_LIST_OF_TAG: 'MEMBER_LIST_OF_TAG',
} as const

export type EventFieldTypeName = (typeof EventFieldTypes)[keyof typeof EventFieldTypes]

export interface EventField {
    id: number
    eventId: number
    name?: string
    fieldType?: string
    config?: Record<string, unknown>
    value?: string
    position: number
    overview?: boolean
    attendanceFieldId?: number | null
    isPublic?: boolean
    registrationLimit?: number | null
}

export interface EventFieldEntry {
    name: string
    fieldType?: string
    config?: Record<string, unknown>
    value?: string
    overview?: boolean
    attendanceFieldId?: number | null
    isPublic?: boolean
    registrationLimit?: number | null
}

export interface SetEventFieldsRequest {
    fields: EventFieldEntry[]
}

export interface BatchFieldEntry {
    name: string
    fieldType?: string
    config?: Record<string, unknown>
    overview?: boolean
    attendanceFieldId?: number | null
}

export interface EventTemplate {
    id: number
    stationId: string
    name: string
    title?: string | null
    description?: string | null
    categoryId?: number | null
    eventType?: string | null
    requiresRegistration?: boolean | null
    registrationDeadlineOffset?: string | null
    requiresConfirmation?: boolean | null
    restrictionMode?: string | null
    attendanceTemplateId?: number | null
    registrationLimit?: number | null
}

export interface EventTemplateField {
    id: number
    templateId: number
    name: string
    fieldType: string
    config: string
    position: number
    overview: boolean
    isPublic: boolean
    attendanceFieldId?: number | null
}

export interface EventTemplateDetail {
    template: EventTemplate
    fields: EventTemplateField[]
    restrictionUserTypes: string[]
    reminderDays: number[]
}

export interface EventTemplateFieldEntry {
    name: string
    fieldType?: string
    config?: Record<string, unknown>
    position: number
    overview?: boolean
    isPublic?: boolean
    registrationLimit?: number | null
    attendanceFieldId?: number | null
}

export const RegistrationStatus = {
    PENDING: 'PENDING',
    ACCEPTED: 'ACCEPTED',
    DENIED: 'DENIED',
    DECLINED: 'DECLINED',
    WITHDRAWN: 'WITHDRAWN',
} as const

export type RegistrationStatusName = (typeof RegistrationStatus)[keyof typeof RegistrationStatus]

export const EventTypes = {
    ONE_TIME: 'ONE_TIME',
    RECURRING: 'RECURRING',
    MONTHLY_FIRST: 'MONTHLY_FIRST',
    QUARTERLY: 'QUARTERLY',
    YEARLY: 'YEARLY',
} as const

export type EventTypeName = (typeof EventTypes)[keyof typeof EventTypes]

export function isRecurringEvent(eventType?: string): boolean {
    return eventType != null && eventType !== EventTypes.ONE_TIME
}

export function needsDayOfWeek(eventType?: string): boolean {
    return eventType === EventTypes.RECURRING || eventType === EventTypes.MONTHLY_FIRST || eventType === EventTypes.QUARTERLY
}

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

interface TemplateCreateRequest {
    name: string
}

interface TemplateUpdateRequest {
    name?: string
    title?: string | null
    description?: string | null
    categoryId?: number | null
    eventType?: string | null
    requiresRegistration?: boolean | null
    registrationDeadlineOffset?: string | null
    requiresConfirmation?: boolean | null
    restrictionMode?: string | null
    attendanceTemplateId?: number | null
    registrationLimit?: number | null
}

const events = createCrudResource<StationEvent, EventRequest>('/events')

const categories = createCrudResource<
    EventCategory,
    CategoryRequest,
    CategoryRequest,
    EventCategory,
    EventCategory,
    unknown
>('/events/categories')

const breaks = createCrudResource<EventBreak, BreakRequest>('/events/breaks')

const templates = createCrudResource<
    EventTemplate,
    TemplateCreateRequest,
    TemplateUpdateRequest,
    EventTemplateDetail,
    EventTemplate,
    void
>('/event-templates')

export async function listUpcomingOccurrences(params?: UpcomingParams): Promise<UpcomingEventOccurrence[]> {
    const res = await client.get<UpcomingEventOccurrence[]>('/events/upcoming', { params })
    return res.data
}

export async function listEvents(params?: EventListParams): Promise<StationEvent[]> {
    return events.list(params ? {...params} : undefined)
}

export async function listTodayEvents(): Promise<StationEvent[]> {
    const res = await client.get<StationEvent[]>('/events/today')
    return res.data
}

export const getEvent = events.get
export const createEvent = events.create
export const updateEvent = events.update
export const deleteEvent = events.remove

export async function cancelEvent(eventId: number, reason?: string): Promise<void> {
    await client.post(`/events/${eventId}/cancel`, { reason: reason ?? null })
}

// -- Categories --

export const listCategories = categories.list
export const createCategory = categories.create
export const updateCategory = categories.update
export const deleteCategory = categories.remove

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
    fields?: RegistrationFieldValue[]
}

/**
 * Configuration of a registration question. The names match the other custom field configs, so the
 * shared config parser reads them without a special case.
 */
export interface EventRegistrationFieldConfig {
    required?: boolean
    defaultValue?: string | null
    options?: string[]
    min?: number | null
    max?: number | null
    groupId?: number | null
    userType?: string | null
    tagId?: number | null
    /** Belongs to whoever runs the event: never asked of a member, never sent to one. */
    managersOnly?: boolean
}

/**
 * A question an event asks of everyone registering for it.
 */
export interface EventRegistrationField {
    id: number
    name: string
    fieldType: EventFieldTypeName
    config: EventRegistrationFieldConfig
    overview: boolean
}

/**
 * A question as the editor submits it, before it has an id of its own.
 */
export interface EventRegistrationFieldDefinition {
    name: string
    fieldType: EventFieldTypeName
    config: EventRegistrationFieldConfig
    overview: boolean
}

/**
 * One member's answer to one registration question.
 */
export interface RegistrationFieldValue {
    fieldId: number
    value: string
}

export async function listRegistrationFields(eventId: number): Promise<EventRegistrationField[]> {
    const res = await client.get<EventRegistrationField[]>(`/events/${eventId}/registration-fields`)
    return res.data
}

export async function setRegistrationFields(
    eventId: number,
    fields: EventRegistrationFieldDefinition[],
): Promise<void> {
    await client.put(`/events/${eventId}/registration-fields`, {fields})
}

export async function updateRegistrationFieldValues(
    registrationId: number,
    fields: RegistrationFieldValue[],
): Promise<EventRegistrationEntry> {
    const res = await client.put<EventRegistrationEntry>(`/events/registrations/${registrationId}/fields`, {fields})
    return res.data
}

export async function setTemplateRegistrationFields(
    templateId: number,
    fields: EventRegistrationFieldDefinition[],
): Promise<void> {
    await client.put(`/event-templates/${templateId}/registration-fields`, {fields})
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
    memberId?: number;
    fields?: RegistrationFieldValue[]
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

/**
 * Changes whether somebody is coming.
 *
 * <p>Open to the member and whoever looks after them while registration is open, and to the people who
 * run the event afterwards. Coming back after declining is a fresh answer, so an event that confirms its
 * list confirms this one too.
 */
export async function changeRegistrationAnswer(id: number, attending: boolean): Promise<void> {
    await client.put(`/events/registrations/${id}/answer`, {attending})
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

export const listBreaks = breaks.list
export const createBreak = breaks.create
export const updateBreak = breaks.update
export const deleteBreak = breaks.remove

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

export async function toggleFieldSelfRegistration(eventId: number, fieldId: number): Promise<EventField> {
    const res = await client.post<EventField>(`/events/${eventId}/fields/${fieldId}/self-register`)
    return res.data
}

// -- Overview Fields --

export async function getOverviewFields(): Promise<Record<number, EventField[]>> {
    const res = await client.get<Record<number, EventField[]>>('/events/overview-fields')
    return res.data
}

// -- Templates --

export const listTemplates = templates.list
export const createTemplate = templates.create
export const getTemplate = templates.get
export const updateTemplate = templates.update
export const deleteTemplate = templates.remove

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
    inlineFields?: BatchFieldEntry[]
    rows: BatchRow[]
    requiresRegistration?: boolean
    requiresConfirmation?: boolean
    registrationDeadline?: string | null
    restriction?: RestrictionSelection
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

// -- Page-editor picker. PAGE_EDIT-gated. --

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
