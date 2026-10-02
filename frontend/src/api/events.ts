/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import {documentFrom, type DocumentFile} from '@/util/documentFile'
import {toIsoDate} from '@/util/format'
import {EventType} from './generated/schema'
import type {
    AbsentMemberResponse,
    AwaitingAnswer,
    BatchCreateRequest,
    BatchRow,
    BlockAudience,
    BreakRequest,
    CancellationNotice,
    CancelledEventDate,
    CategoryRequest,
    CreateTemplateRequest,
    DatedEvent,
    EmbeddedEvent,
    EmbedReference,
    EnrichedFederationRegistration,
    EventAttachment,
    EventBreak,
    EventCategory,
    AppointmentField,
    EventFieldDefault,
    EventRegistration,
    EventRegistrationFieldValue,
    EventRegistrationOpening,
    EventRequest,
    EventRestrictions,
    EventSummary,
    EventTemplate,
    EventTemplateFieldData,
    FederatedEventItem,
    FederatedRegistrationAnswer,
    FederationShareResponse,
    FieldDefaultEntry,
    GenerateDatesRequest,
    MessageResponse,
    NextDate,
    PartnerPlacesView,
    PickerEvent,
    RegistrationCount,
    RegistrationFieldDefinition,
    EventRegistrationField,
    RegistrationResponse,
    RegistrationStatsResponse,
    RegistrationStatus,
    RemoteAttachment,
    PartnerEventDetail,
    RegistrationTemplateField,
    RemoteMemberRegistration,
    SetEventFieldsRequest,
    ShareScope,
    StationEvent,
    TemplateDetailResponse,
    TemplateRestrictions,
    UpcomingEventOccurrence,
    UpdateTemplateRequest,
    WithdrawalResponse,
} from './generated/schema'

/** An appointment as either the appointment itself or a list of appointments sends it. */
export type AnyEvent = StationEvent | EventSummary

/**
 * Whom the reader may see restricted, by appointment id: only the appointments that restrict
 * anybody are in it.
 */
export type AllEventRestrictions = Record<number, EventRestrictions>

export function isRecurringEvent(eventType?: string | null): boolean {
    return eventType != null && eventType !== EventType.ONE_TIME
}

/**
 * Whether a quarterly appointment comes round in this month: every third month counted from the
 * month it starts in, which is how the server and a subscribed calendar count it too.
 *
 * @param month zero-based, as `Date` counts months
 */
export function isQuarterMonthOf(event: AnyEvent, year: number, month: number): boolean {
    const start = new Date(event.startTime)
    const monthsSinceStart = (year - start.getFullYear()) * 12 + (month - start.getMonth())
    return ((monthsSinceStart % 3) + 3) % 3 === 0
}

/**
 * The last day of a one-off appointment that runs past the day it starts on, or `null` where it
 * ends on that day. A series never spans days: each of its occurrences is an entry of its own.
 */
export function multiDayEndDate(event: AnyEvent, startDay: string): string | null {
    if (isRecurringEvent(event.eventType)) return null
    const endDay = toIsoDate(new Date(event.endTime))
    return endDay > startDay ? endDay : null
}

export function needsDayOfWeek(eventType?: string | null): boolean {
    return eventType === EventType.RECURRING || eventType === EventType.MONTHLY_FIRST || eventType === EventType.QUARTERLY
}

export interface EventListParams {
    categoryId?: number
    requiresRegistration?: boolean
}

/**
 * What a list of occurrences is asked for: which appointments to consider, which stretch of days to
 * look at and which page of the answer to hand back.
 *
 * `from` and `to` are ISO dates (yyyy-MM-dd) and are both inclusive. The upcoming list starts at
 * today and the past list ends at yesterday, whatever the window says.
 */
export interface OccurrenceParams {
    categoryId?: number
    requiresRegistration?: boolean
    search?: string
    from?: string
    to?: string
    limit?: number
    offset?: number
}

/** Whether a page of appointments wants the ones that still come round or the ones that do not. */
export const EventStates = {
    CURRENT: 'current',
    PAST: 'past',
} as const

export type EventStateName = (typeof EventStates)[keyof typeof EventStates]

/** Which kind of appointment a page wants, the two being listed apart because they read apart. */
export const EventKinds = {
    ONE_TIME: 'one_time',
    REPEATING: 'repeating',
} as const

export type EventKindName = (typeof EventKinds)[keyof typeof EventKinds]

/**
 * What a page of appointments is asked for.
 *
 * `state` defaults to the current ones, and leaving `kind` out asks for both kinds together. The
 * window bounds the date the page is ordered by: the next date for the current ones, the date it
 * last fell on for the past ones.
 */
export interface EventPageParams extends OccurrenceParams {
    state?: EventStateName
    kind?: EventKindName
}

/**
 * One occurrence of an appointment, which is the appointment and the date together.
 *
 * An occurrence carries no id of its own: a recurring appointment named without a date would mean
 * every occurrence there has ever been.
 */
export interface EventOccurrenceRef {
    eventId: number
    date: string
}

const events = createCrudResource<EventSummary, EventRequest, EventRequest, StationEvent, StationEvent>('/events')

const categories = createCrudResource<
    EventCategory,
    CategoryRequest,
    CategoryRequest,
    EventCategory,
    EventCategory,
    MessageResponse
>('/events/categories')

const breaks = createCrudResource<EventBreak, BreakRequest>('/events/breaks')

const templates = createCrudResource<
    EventTemplate,
    CreateTemplateRequest,
    UpdateTemplateRequest,
    TemplateDetailResponse,
    EventTemplate,
    EventTemplate
>('/event-templates')

/** The occurrences from today on, earliest first, a page at a time. */
export async function listUpcomingOccurrences(params?: OccurrenceParams): Promise<UpcomingEventOccurrence[]> {
    const res = await client.get<UpcomingEventOccurrence[]>('/events/upcoming', { params })
    return res.data
}

/** The occurrences before today, newest first, a page at a time. */
export async function listPastOccurrences(params?: OccurrenceParams): Promise<UpcomingEventOccurrence[]> {
    const res = await client.get<UpcomingEventOccurrence[]>('/events/past', { params })
    return res.data
}

/**
 * A page of appointments themselves rather than of their occurrences, each with the date it next
 * falls on.
 */
export async function listPagedEvents(params?: EventPageParams): Promise<DatedEvent[]> {
    const res = await client.get<DatedEvent[]>('/events/paged', { params })
    return res.data
}

export async function listEvents(params?: EventListParams): Promise<EventSummary[]> {
    return events.list(params ? {...params} : undefined)
}

export async function listTodayEvents(): Promise<EventSummary[]> {
    const res = await client.get<EventSummary[]>('/events/today')
    return res.data
}

export const getEvent = events.get

/**
 * The next day an appointment falls on, today counting as next, or nothing where it has no date.
 *
 * <p>Asked of the server rather than worked out here: only it knows the rule the appointment
 * repeats by, the weeks the station is off and the date the series runs to.
 */
export async function getNextDate(eventId: number): Promise<string | null> {
    const res = await client.get<NextDate>(`/events/${eventId}/next-date`)
    return res.data.date
}

export const createEvent = events.create
export const updateEvent = events.update
export const deleteEvent = events.remove

/** Cancels a whole series for good. A one-time appointment is cancelled by its date instead. */
export async function cancelSeries(eventId: number, reason?: string): Promise<void> {
    await client.post(`/events/${eventId}/cancel`, { reason: reason ?? null })
}

/** Cancels one date of an appointment, leaving every other date of a series as it is. */
export async function cancelEventDate(eventId: number, date: string, reason?: string): Promise<void> {
    await client.post(`/events/${eventId}/dates/${date}/cancel`, { reason: reason ?? null })
}

/** Brings a cancelled date back; the places kept on it stand again. */
export async function restoreEventDate(eventId: number, date: string): Promise<void> {
    await client.post(`/events/${eventId}/dates/${date}/restore`)
}

/**
 * The dates cancelled one by one of every appointment the reader sees. A whole series cancelled says
 * so on the appointment itself.
 */
export async function listStationCancelledDates(): Promise<CancelledEventDate[]> {
    const res = await client.get<CancelledEventDate[]>('/events/cancellations')
    return res.data
}

/** The dates of an appointment that are cancelled one by one, earliest first. */
export async function listCancelledDates(eventId: number): Promise<CancellationNotice[]> {
    const res = await client.get<CancellationNotice[]>(`/events/${eventId}/cancellations`)
    return res.data
}

export const listCategories = categories.list
export const createCategory = categories.create
export const updateCategory = categories.update
export const deleteCategory = categories.remove

export async function reorderCategories(orderedIds: number[]): Promise<EventCategory[]> {
    const res = await client.put<EventCategory[]>('/events/categories/reorder', {orderedIds})
    return res.data
}

export async function listRegistrationFields(eventId: number): Promise<EventRegistrationField[]> {
    const res = await client.get<EventRegistrationField[]>(`/events/${eventId}/registration-fields`)
    return res.data
}

export async function setRegistrationFields(
    eventId: number,
    fields: RegistrationFieldDefinition[],
): Promise<void> {
    await client.put(`/events/${eventId}/registration-fields`, {fields})
}

export async function updateRegistrationFieldValues(
    registrationId: number,
    fields: EventRegistrationFieldValue[],
): Promise<RegistrationResponse> {
    const res = await client.put<RegistrationResponse>(`/events/registrations/${registrationId}/fields`, {fields})
    return res.data
}

export async function setTemplateRegistrationFields(
    templateId: number,
    fields: RegistrationFieldDefinition[],
): Promise<RegistrationTemplateField[]> {
    const res = await client.put<RegistrationTemplateField[]>(
        `/event-templates/${templateId}/registration-fields`,
        {fields},
    )
    return res.data
}

export async function listMyRegistrations(): Promise<RegistrationResponse[]> {
    const res = await client.get<RegistrationResponse[]>('/events/registrations/mine')
    return res.data
}

export async function listPendingRegistrations(): Promise<RegistrationResponse[]> {
    const res = await client.get<RegistrationResponse[]>('/events/registrations/pending')
    return res.data
}

export async function listEventRegistrations(eventId: number, date?: string): Promise<RegistrationResponse[]> {
    const params = date ? { date } : undefined
    const res = await client.get<RegistrationResponse[]>(`/events/${eventId}/registrations`, { params })
    return res.data
}

export async function listAbsencesForDate(eventId: number, date: string): Promise<AbsentMemberResponse[]> {
    const res = await client.get<AbsentMemberResponse[]>(`/events/${eventId}/absences`, { params: { date } })
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
 * Whom the reader may sign up for each appointment they can see, by appointment.
 *
 * <p>Every appointment the reader can see is answered, those open to nobody here with an empty list,
 * so an appointment missing from the result is one the answer is not known for.
 */
export async function listEligibleMembers(): Promise<Record<number, number[]>> {
    const res = await client.get<EventRegistrationOpening[]>('/events/eligible-members')
    return Object.fromEntries(res.data.map(opening => [opening.eventId, opening.memberIds]))
}

export async function registerForEvent(eventId: number, data: {
    eventDate?: string;
    memberId?: number;
    fields?: EventRegistrationFieldValue[]
}): Promise<RegistrationResponse> {
    const res = await client.post<RegistrationResponse>(`/events/${eventId}/register`, data)
    return res.data
}

export async function declineEvent(eventId: number, data: { eventDate?: string; memberId?: number }): Promise<EventRegistration> {
    const res = await client.post<EventRegistration>(`/events/${eventId}/decline`, data)
    return res.data
}

export async function listRegistrationCounts(): Promise<RegistrationCount[]> {
    const res = await client.get<RegistrationCount[]>('/events/registrations/counts')
    return res.data
}

/**
 * How long a withdrawal is offered back where the server does not say.
 *
 * <p>The server is the authority and answers with its own deadline wherever it can. A partner
 * station's appointment is the exception: the answer travels through two instances and carries no
 * deadline, so the offer is shown for the length everybody uses and refused by whoever holds the row
 * if it has run out.
 */
export const UNDO_WINDOW_MS = 5 * 60 * 1000

export async function withdrawRegistration(id: number): Promise<WithdrawalResponse> {
    const res = await client.delete<WithdrawalResponse>(`/events/registrations/${id}`)
    return res.data
}

/**
 * Puts a withdrawal back, which the server allows for a few minutes and refuses afterwards.
 *
 * <p>What comes back is the place that was held rather than a fresh answer, so somebody who pressed
 * the wrong button is where they were rather than at the end of the queue.
 */
export async function undoWithdrawal(id: number): Promise<void> {
    await client.post(`/events/registrations/${id}/undo`)
}

/** Events whose registration closes soon and which the household has not answered. */
export async function listAwaitingAnswer(): Promise<AwaitingAnswer[]> {
    const res = await client.get<AwaitingAnswer[]>('/events/registrations/awaiting')
    return res.data
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

export async function updateRegistrationStatus(id: number, status: RegistrationStatus): Promise<MessageResponse> {
    const res = await client.put<MessageResponse>(`/events/registrations/${id}/status`, {status})
    return res.data
}

export async function getFieldDefaults(eventId: number): Promise<EventFieldDefault[]> {
    const res = await client.get<EventFieldDefault[]>(`/events/${eventId}/field-defaults`)
    return res.data
}

export async function setFieldDefaults(eventId: number, data: FieldDefaultEntry[]): Promise<EventFieldDefault[]> {
    const res = await client.put<EventFieldDefault[]>(`/events/${eventId}/field-defaults`, data)
    return res.data
}

export const listBreaks = breaks.list
export const createBreak = breaks.create
export const updateBreak = breaks.update
export const deleteBreak = breaks.remove

export async function listFieldNames(): Promise<string[]> {
    const res = await client.get<string[]>('/events/field-names')
    return res.data
}

export async function exportEventList(data: {
    categoryIds?: number[]
    columns?: { type: string; key?: string; fieldName?: string; label: string }[]
    from: string
    to: string
}): Promise<DocumentFile> {
    const res = await client.post('/events/export', data, {responseType: 'blob'})
    return documentFrom(res, 'Termine.pdf')
}

/**
 * The questions of an appointment. With a date, as they stand on that occurrence; without one, as
 * the appointment itself carries them, which is what the editor edits.
 */
export async function getEventFields(eventId: number, date?: string | null): Promise<AppointmentField[]> {
    const res = await client.get<AppointmentField[]>(`/events/${eventId}/fields`, {params: date ? {date} : undefined})
    return res.data
}

/** Writes the answer one question carries on one date. */
export async function setEventFieldValueOn(
    eventId: number,
    fieldId: number,
    date: string,
    value: string,
): Promise<AppointmentField> {
    const res = await client.put<AppointmentField>(`/events/${eventId}/fields/${fieldId}/value`, {date, value})
    return res.data
}

export async function setEventFields(eventId: number, data: SetEventFieldsRequest): Promise<AppointmentField[]> {
    const res = await client.put<AppointmentField[]>(`/events/${eventId}/fields`, data)
    return res.data
}

export async function toggleFieldSelfRegistration(
    eventId: number,
    fieldId: number,
    date?: string | null,
): Promise<AppointmentField> {
    const res = await client.post<AppointmentField>(
        `/events/${eventId}/fields/${fieldId}/self-register`,
        undefined,
        {params: date ? {date} : undefined},
    )
    return res.data
}

export async function getOverviewFields(): Promise<Record<number, AppointmentField[]>> {
    const res = await client.get<Record<number, AppointmentField[]>>('/events/overview-fields')
    return res.data
}

export const listTemplates = templates.list
export const createTemplate = templates.create
export const getTemplate = templates.get
export const updateTemplate = templates.update
export const deleteTemplate = templates.remove

export async function setTemplateFields(id: number, data: { fields: EventTemplateFieldData[] }): Promise<void> {
    await client.put(`/event-templates/${id}/fields`, data)
}

export async function setTemplateRestrictions(id: number, data: TemplateRestrictions): Promise<TemplateRestrictions> {
    const res = await client.put<TemplateRestrictions>(`/event-templates/${id}/restrictions`, data)
    return res.data
}

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

export async function generateDates(data: GenerateDatesRequest): Promise<BatchRow[]> {
    const res = await client.post<BatchRow[]>('/events/batch/generate-dates', data)
    return res.data
}

export async function createBatchEvents(data: BatchCreateRequest): Promise<StationEvent[]> {
    const res = await client.post<StationEvent[]>('/events/batch', data)
    return res.data
}

export async function getRegistrationStats(eventId: number, categoryId?: number, months?: number): Promise<RegistrationStatsResponse[]> {
    const params: Record<string, string> = {}
    if (categoryId != null) params.categoryId = String(categoryId)
    if (months != null) params.months = String(months)
    const res = await client.get<RegistrationStatsResponse[]>(`/events/${eventId}/registration-stats`, {params})
    return res.data
}

export async function listFederatedEvents(): Promise<FederatedEventItem[]> {
    const res = await client.get<FederatedEventItem[]>('/federated/events')
    return res.data
}

/**
 * Gives one of this station's own members a place at a partner's appointment.
 *
 * <p>Only where that station handed the choosing over. The places are theirs and so is the counting,
 * so a refusal means the places are full rather than that anything went wrong.
 */
export async function confirmOwnFederatedMember(stationUid: string, eventId: number, eventDate: string, memberId: string): Promise<void> {
    await client.post(`/federated/${stationUid}/events/${eventId}/register/confirm`, {eventDate, memberId})
}

export async function getFederatedEvent(stationUid: string, eventId: number): Promise<PartnerEventDetail> {
    const res = await client.get<PartnerEventDetail>(`/federated/${stationUid}/events/${eventId}`)
    return res.data
}

export async function listMyFederatedRegistrations(): Promise<RemoteMemberRegistration[]> {
    const res = await client.get<RemoteMemberRegistration[]>('/federated/my-registrations')
    return res.data
}

/**
 * Signs a member up for a partner station's appointment and answers with what they recorded.
 *
 * <p>The status is theirs to decide: an appointment that asks for no confirmation accepts at once,
 * and showing a pending badge regardless would tell the member something nobody said.
 */
export async function registerForFederatedEvent(stationUid: string, eventId: number, eventDate: string, memberId?: string): Promise<RegistrationStatus> {
    const res = await client.post<FederatedRegistrationAnswer>(`/federated/${stationUid}/events/${eventId}/register`, { eventDate, memberId: memberId ?? null })
    return res.data.status
}

export async function withdrawFederatedRegistration(stationUid: string, eventId: number, eventDate: string, memberId?: string): Promise<void> {
    await client.delete(`/federated/${stationUid}/events/${eventId}/register`, { data: { eventDate, memberId: memberId ?? null } })
}

/**
 * Asks the station holding the appointment to put a place back after a withdrawal.
 *
 * <p>Their clock decides, because the registration is theirs. A refusal means the few minutes have
 * passed rather than that anything went wrong.
 */
export async function undoFederatedWithdrawal(stationUid: string, eventId: number, eventDate: string, memberId?: string): Promise<void> {
    await client.post(`/federated/${stationUid}/events/${eventId}/register/undo`, { eventDate, memberId: memberId ?? null })
}

export async function listFederationRegistrations(eventId: number, date?: string): Promise<EnrichedFederationRegistration[]> {
    const params = date ? { date } : {}
    const res = await client.get<EnrichedFederationRegistration[]>(`/events/${eventId}/federation-registrations`, { params })
    return res.data
}

export async function updateFederationRegistrationStatus(registrationId: number, status: RegistrationStatus): Promise<void> {
    await client.put(`/events/federation-registrations/${registrationId}/status`, { status })
}

export async function getFederationShare(eventId: number): Promise<FederationShareResponse> {
    const res = await client.get<FederationShareResponse>(`/events/${eventId}/federation`)
    return res.data
}

export async function setFederationShare(eventId: number, scope: ShareScope, partnerIds?: number[]): Promise<void> {
    await client.put(`/events/${eventId}/federation`, {scope, partnerIds: partnerIds ?? []})
}

export async function removeFederationShare(eventId: number): Promise<void> {
    await client.delete(`/events/${eventId}/federation`)
}

/**
 * What each partner station may do with a shared appointment.
 *
 * <p>Absent from the list means the arrangement nobody configured: this station decides, member by
 * member, with no cap. That is how every shared appointment worked before any of this.
 */
export async function getPartnerPlaces(eventId: number): Promise<PartnerPlacesView[]> {
    const res = await client.get<PartnerPlacesView[]>(`/events/${eventId}/partner-places`)
    return res.data
}

/**
 * Hands a partner a number of places, or takes the arrangement back.
 *
 * <p>A budget means the partner decides: handing somebody places and then choosing their people for
 * them is not a thing anybody wants, so the two travel as one choice with an optional number.
 */
export async function setPartnerPlaces(eventId: number, partnerId: number, slotBudget: number | null, partnerConfirms: boolean): Promise<void> {
    await client.put(`/events/${eventId}/partner-places/${partnerId}`, {slotBudget, partnerConfirms})
}

/** Which appointments the picker offers, read as a query parameter. */
export type EventPickerMode = 'FUTURE' | 'PAST' | 'ALL'

/**
 * Searches the appointments a block may name for its readers: on a page (`PUBLIC`) those on the
 * public calendar, in a news or wiki article (`MEMBERS`) every one every member may see.
 */
export async function searchEvents(
    query?: string,
    mode: EventPickerMode = 'FUTURE',
    limit = 10,
    scope: BlockAudience = 'PUBLIC',
): Promise<PickerEvent[]> {
    const params: Record<string, string | number> = {mode, limit, scope}
    if (query) params.q = query
    const res = await client.get<PickerEvent[]>('/events/search', {params})
    return res.data
}

/**
 * An event of the reader's own station named by its public id, when every member may see it.
 * Answers 404 for an event that is missing or kept to part of the station.
 */
export async function getEmbeddedEvent(eventUid: string): Promise<EmbeddedEvent> {
    const res = await client.get<EmbeddedEvent>(`/events/embed/${encodeURIComponent(eventUid)}`)
    return res.data
}

/** The public id an event block names this event by. NEWS_EDIT or PAGE_EDIT gated. */
export async function getEmbedReference(eventId: number): Promise<string> {
    const res = await client.get<EmbedReference>(`/events/${eventId}/embed-reference`)
    return res.data.eventUid
}

/** The files of an event, as far as the caller may have them. */
export async function listEventAttachments(eventId: number): Promise<EventAttachment[]> {
    const res = await client.get<EventAttachment[]>(`/events/${eventId}/attachments`)
    return res.data
}

export async function attachEventFile(
    eventId: number,
    fileId: number,
    label: string | null,
    internal: boolean,
): Promise<EventAttachment> {
    const res = await client.post<EventAttachment>(`/events/${eventId}/attachments`, {fileId, label, internal})
    return res.data
}

export async function updateEventAttachment(
    eventId: number,
    attachmentId: number,
    label: string | null,
    internal: boolean,
): Promise<void> {
    await client.put(`/events/${eventId}/attachments/${attachmentId}`, {label, internal})
}

export async function reorderEventAttachments(eventId: number, attachmentIds: number[]): Promise<void> {
    await client.put(`/events/${eventId}/attachments/order`, {attachmentIds})
}

export async function detachEventFile(eventId: number, attachmentId: number): Promise<void> {
    await client.delete(`/events/${eventId}/attachments/${attachmentId}`)
}

/** Where the bytes of a file are fetched from, which is the event's own address rather than the library's. */
export function eventAttachmentUrl(eventId: number, attachmentId: number): string {
    return `/events/${eventId}/attachments/${attachmentId}/file`
}

/**
 * Where the picture of a file is fetched from, at the width the tile showing it wants.
 *
 * <p>Refused for a file that has none, which is what tells a tile to draw the kind of file instead.
 */
export function eventAttachmentPictureUrl(eventId: number, attachmentId: number, width?: number): string {
    const base = `/events/${eventId}/attachments/${attachmentId}/picture`
    return width ? `${base}?w=${width}` : base
}

export async function listFederatedEventAttachments(
    stationUid: string,
    eventId: number,
): Promise<RemoteAttachment[]> {
    const res = await client.get<RemoteAttachment[]>(`/federated/${stationUid}/events/${eventId}/attachments`)
    return res.data
}

export function federatedEventAttachmentUrl(stationUid: string, eventId: number, attachmentId: number): string {
    return `/federated/${stationUid}/events/${eventId}/attachments/${attachmentId}/file`
}
