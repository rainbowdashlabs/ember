/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {documentFrom, type DocumentFile} from '@/util/documentFile'
import type {ExportSeparator} from '@/util/exportFormat'
import type {
    EventGroupAttendance,
    EventGroupAwaiting,
    EventGroupCreateRequest,
    EventGroupDetail,
    EventGroupPartnerSignUp,
    EventGroupPartCreateRequest,
    EventGroupPartSummary,
    EventGroupSignUp,
    EventGroupSummary,
    EventGroupDetails,
    EventGroupPart,
    EventFieldEntry,
    EventGroupSetDraft,
    RegistrationFieldDefinition,
    EventRegistrationFieldValue,
    FederationShareResponse,
    RegistrationStatus,
    RestrictionAudience,
    ShareScope,
} from './generated/schema'

const EVENT_GROUPS = '/events/groups'

/** The event groups meant for the reader, or every event group for whoever runs events. */
export async function listEventGroups(): Promise<EventGroupSummary[]> {
    return (await client.get<EventGroupSummary[]>(EVENT_GROUPS)).data
}

/** An event group with its sets, parts and the sign-ups of the reader's household. */
export async function getEventGroup(eventGroupId: number): Promise<EventGroupDetail> {
    return (await client.get<EventGroupDetail>(`${EVENT_GROUPS}/${eventGroupId}`)).data
}

/** Creates an event group with its sets and audiences. */
export async function createEventGroup(request: EventGroupCreateRequest): Promise<EventGroupDetail> {
    return (await client.post<EventGroupDetail>(EVENT_GROUPS, request)).data
}

/**
 * Saves what the event group editor holds in one go: details, and where given its sets, audiences,
 * questions and fields. Either all of it is kept or, where any part is refused, none of it.
 */
export async function updateEventGroup(
    eventGroupId: number,
    details: EventGroupDetails,
    sets?: EventGroupSetDraft[],
    audiences?: {registration: RestrictionAudience; view: RestrictionAudience},
    questions?: RegistrationFieldDefinition[],
    fields?: EventFieldEntry[],
): Promise<EventGroupDetail> {
    return (await client.put<EventGroupDetail>(`${EVENT_GROUPS}/${eventGroupId}`, {details, sets, ...audiences, questions, fields})).data
}

/** Deletes an event group; its parts become appointments of their own. */
export async function deleteEventGroup(eventGroupId: number): Promise<void> {
    await client.delete(`${EVENT_GROUPS}/${eventGroupId}`)
}

/** Calls an event group off with every part still to come. */
export async function cancelEventGroup(eventGroupId: number, reason: string | null): Promise<EventGroupDetail> {
    return (await client.post<EventGroupDetail>(`${EVENT_GROUPS}/${eventGroupId}/cancel`, {reason})).data
}

/** Replaces the sets of an event group, in their new order. */
export async function replaceSets(eventGroupId: number, sets: EventGroupSetDraft[]): Promise<EventGroupDetail> {
    return (await client.put<EventGroupDetail>(`${EVENT_GROUPS}/${eventGroupId}/sets`, {sets})).data
}

/** Sets who may sign up for an event group and who may see it. */
export async function setAudiences(
    eventGroupId: number,
    registration: RestrictionAudience,
    view: RestrictionAudience,
): Promise<EventGroupDetail> {
    return (await client.put<EventGroupDetail>(`${EVENT_GROUPS}/${eventGroupId}/audiences`, {registration, view})).data
}

/** Makes an existing one-time appointment a part of an event group. */
export async function addPart(eventGroupId: number, eventId: number, setId: number): Promise<EventGroupPart> {
    return (await client.post<EventGroupPart>(`${EVENT_GROUPS}/${eventGroupId}/parts`, {eventId, setId})).data
}

/** Creates a new appointment straight into an event group. */
export async function createPart(eventGroupId: number, request: EventGroupPartCreateRequest): Promise<EventGroupPart> {
    return (await client.post<EventGroupPart>(`${EVENT_GROUPS}/${eventGroupId}/parts/new`, request)).data
}

/** Moves a part into another set of its event group. */
export async function movePart(eventGroupId: number, eventId: number, setId: number): Promise<EventGroupPart> {
    return (await client.put<EventGroupPart>(`${EVENT_GROUPS}/${eventGroupId}/parts/${eventId}`, {setId})).data
}

/** Makes a part an appointment of its own again. */
export async function removePart(eventGroupId: number, eventId: number): Promise<void> {
    await client.delete(`${EVENT_GROUPS}/${eventGroupId}/parts/${eventId}`)
}

/** Signs the reader, or somebody they look after, up with a choice of parts and the answers to the event group's questions. */
export async function signUp(
    eventGroupId: number,
    parts: number[],
    answers: EventRegistrationFieldValue[],
    memberId?: number,
): Promise<EventGroupSignUp> {
    return (await client.post<EventGroupSignUp>(`${EVENT_GROUPS}/${eventGroupId}/sign-up`, {parts, answers, memberId})).data
}

/** Changes the answers given with a standing sign-up. */
export async function changeAnswers(
    eventGroupId: number,
    answers: EventRegistrationFieldValue[],
    memberId?: number,
): Promise<EventGroupSignUp> {
    return (await client.put<EventGroupSignUp>(`${EVENT_GROUPS}/${eventGroupId}/sign-up/answers`, {answers, memberId})).data
}

/** Changes the parts chosen with a sign-up. */
export async function changeParts(eventGroupId: number, parts: number[], memberId?: number): Promise<EventGroupSignUp> {
    return (await client.put<EventGroupSignUp>(`${EVENT_GROUPS}/${eventGroupId}/sign-up`, {parts, memberId})).data
}

/** Withdraws a sign-up with every part chosen. */
export async function withdraw(eventGroupId: number, memberId?: number): Promise<EventGroupSignUp> {
    return (await client.delete<EventGroupSignUp>(`${EVENT_GROUPS}/${eventGroupId}/sign-up`, {params: {memberId}})).data
}

/** Every sign-up of an event group that holds, waits for or was turned down for a place. */
export async function listSignUps(eventGroupId: number): Promise<EventGroupSignUp[]> {
    return (await client.get<EventGroupSignUp[]>(`${EVENT_GROUPS}/${eventGroupId}/sign-ups`)).data
}

/** Accepts or turns down a sign-up and every part chosen with it. */
export async function decide(eventGroupId: number, memberId: number, status: RegistrationStatus): Promise<EventGroupSignUp> {
    return (await client.put<EventGroupSignUp>(`${EVENT_GROUPS}/${eventGroupId}/sign-ups/${memberId}/status`, {status})).data
}

/** The event group an appointment is a part of, or null for an appointment of its own. */
export async function eventGroupOfEvent(eventId: number): Promise<EventGroupPartSummary | null> {
    const res = await client.get<EventGroupPartSummary | ''>(`/events/${eventId}/group`)
    return res.status === 204 || !res.data ? null : res.data
}

/** The event groups still waiting on an answer from the reader or anybody they look after. */
export async function listAwaiting(): Promise<EventGroupAwaiting[]> {
    return (await client.get<EventGroupAwaiting[]>(`${EVENT_GROUPS}/awaiting`)).data
}

/** How an event group is shared with partner stations. */
export async function getFederationShare(eventGroupId: number): Promise<FederationShareResponse> {
    return (await client.get<FederationShareResponse>(`${EVENT_GROUPS}/${eventGroupId}/federation`)).data
}

/** Shares an event group and every part with partner stations. */
export async function setFederationShare(eventGroupId: number, scope: ShareScope, partnerIds?: number[]): Promise<void> {
    await client.put(`${EVENT_GROUPS}/${eventGroupId}/federation`, {scope, partnerIds: partnerIds ?? []})
}

/** Stops sharing an event group and its parts. */
export async function removeFederationShare(eventGroupId: number): Promise<void> {
    await client.delete(`${EVENT_GROUPS}/${eventGroupId}/federation`)
}

/** The sign-ups partner stations' members sent for an event group. */
export async function listPartnerSignUps(eventGroupId: number): Promise<EventGroupPartnerSignUp[]> {
    return (await client.get<EventGroupPartnerSignUp[]>(`${EVENT_GROUPS}/${eventGroupId}/partner-sign-ups`)).data
}

/** Accepts or turns down a partner member's sign-up and every part chosen with it. */
export async function decidePartner(eventGroupId: number, signUpId: number, status: RegistrationStatus): Promise<void> {
    await client.put(`${EVENT_GROUPS}/${eventGroupId}/partner-sign-ups/${signUpId}/status`, {status})
}

/** How the accepted participants of an event group attended its parts. */
export async function listAttendance(eventGroupId: number): Promise<EventGroupAttendance[]> {
    return (await client.get<EventGroupAttendance[]>(`${EVENT_GROUPS}/${eventGroupId}/attendance`)).data
}

/** The attendance of an event group's participants as a spreadsheet. */
export async function exportAttendance(eventGroupId: number, separator: ExportSeparator = 'semicolon'): Promise<DocumentFile> {
    const res = await client.get(`${EVENT_GROUPS}/${eventGroupId}/attendance/export.csv`, {responseType: 'blob', params: {separator}})
    return documentFrom(res, 'Terminreihe.csv')
}
